package com.example.demo

import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQueryBuilders
import co.elastic.clients.json.JsonData
import com.example.demo.products.documents.Product
import com.example.demo.products.repositories.ProductRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import java.time.LocalDateTime
import kotlin.test.assertContentEquals

@SpringBootTest
class ElasticsearchQueryTests {
    @Autowired
    lateinit var productRepository: ProductRepository

    @Autowired
    lateinit var elasticsearchOperations: ElasticsearchOperations

    @BeforeEach
    fun setUp() {
        productRepository.deleteAll()
        listOf(
            Product(
                name = "Apple iPhone 13",
                description = "Latest model of iPhone with advanced features",
                price = 999,
                category = "Electronics",
                stock = 100,
                createdAt = LocalDateTime.now(),
                available = true
            ),
            Product(
                name = "Samsung Galaxy S21",
                description = "Flagship smartphone from Samsung with great performance",
                price = 899,
                category = "Electronics",
                stock = 150,
                createdAt = LocalDateTime.now(),
                available = true
            ),
            Product(
                name = "Apple MacBook Pro",
                description = "High-performance laptop for professionals",
                price = 1999,
                category = "Computers",
                stock = 50,
                createdAt = LocalDateTime.now(),
                available = true
            )
        ).let(productRepository::saveAll)

    }

    @Test
    fun `기본적인 문서 생성 작업`() {
        val product = Product(
            name = "Sample Product",
            description = "This is a sample product",
            price = 2000,
            category = "Sample Category",
            stock = 50,
            createdAt = LocalDateTime.now(),
            available = true
        ).let(productRepository::save)
        assertNotNull(product.id)
    }

    @Test
    fun `Text 필드 검색 예제`() {
        println("ElasticsearchRepository 를 사용한 검색")

        println("1. Text 필드에 \"Apple\"이 포함된 문서 검색")
        val appleProducts = productRepository.findByName("Apple")
        assert(appleProducts.size == 2)
        appleProducts.forEach {
            println(it.name)
        }

        println("2. Text 필드에 여러 값이 포함된 문서 검색")
        val multiProducts = productRepository.findByNameIn(listOf("Apple", "Samsung"))
        assert(multiProducts.size == 3)
        multiProducts.forEach {
            println(it.name)
        }

        println("ElasticsearchOperations 를 사용한 검색")

        println("1. Text 필드에 \"Apple\"이 포함된 문서 검색")
        // Text 필드는 토큰화되므로 match 쿼리를 사용해야 합니다.
        // term 쿼리는 Keyword 필드에 사용합니다.
        val appleQuery = NativeQueryBuilder().withQuery { q ->
            q.match { m -> m.field("name").query("Apple") }
        }.build()
        val appleProductsWithNativeQuery = elasticsearchOperations.search(
            appleQuery,
            Product::class.java
        )
        assertContentEquals(appleProducts, appleProductsWithNativeQuery.map { it.content })
        appleProductsWithNativeQuery.forEach {
            println(it.content.name)
        }

        println("Text 필드에 여러 값이 포함된 문서 검색")

        println("1: Bool + Should (각 값마다 should 절 추가)")
        val multiQueryWithBool = NativeQueryBuilder().withQuery { q ->
            q.bool { b ->
                b.should { s -> s.match { m -> m.field("name").query("Apple") } }
                b.should { s -> s.match { m -> m.field("name").query("Samsung") } }
            }
        }.build()
        val multiQueryWithBoolQuery = elasticsearchOperations.search(
            multiQueryWithBool,
            Product::class.java
        )
        multiQueryWithBoolQuery.forEach {
            println(it.content.name)
        }

        println("2: simple_query_string - 여러 값을 OR로 연결하여 더 간결하게 작성")
        val multiQueryWithSimpleQueryString = NativeQueryBuilder().withQuery { q ->
            q.simpleQueryString { s ->
                s.query("Apple | Samsung")
                    .fields("name")
            }
        }.build()
        val multiProductsWithSimpleQueryStringQuery = elasticsearchOperations.search(
            multiQueryWithSimpleQueryString,
            Product::class.java
        )
        multiProductsWithSimpleQueryStringQuery.forEach {
            println(it.content.name)
        }
        assertContentEquals(multiProducts, multiQueryWithBoolQuery.map { it.content })
        assertContentEquals(multiProducts, multiProductsWithSimpleQueryStringQuery.map { it.content })

        println("Keyword 필드로 검색 했을 때와의 차이점 확인")
        val keywordQuery = NativeQueryBuilder().withQuery { q ->
            q.term { t -> t.field("name").value("Apple") }
        }.build()
        val keywordProducts = elasticsearchOperations.search(
            keywordQuery,
            Product::class.java
        )
        keywordProducts.takeIf { it.none() }.let {
            println("Keyword 필드로는 'Apple' 단독 검색 시 문서가 검색되지 않음")
        }
        assert(keywordProducts.isEmpty)
    }
	@Test
	fun `Number 필드 검색 예제`(){
		println("ElasticsearchRepository 를 사용한 검색")

		println("1. 가격이 1000 이상인 문서 검색")
		val price1000Products = productRepository.findByPriceGreaterThanEqual(1000)
		price1000Products.forEach {
			println("${it.name} - ${it.price}")
		}
		assert(price1000Products.size == 1)

		println("2. 가격이 900 이하인 문서 검색")
		val price900Products = productRepository.findByPriceLessThanEqual(900)
		price900Products.forEach {
			println("${it.name} - ${it.price}")
		}
		assert(price900Products.size == 1)

		println("3. 가격이 900 이상 1000 이하인 문서 검색")
		val price900to1000Products = productRepository.findByPriceBetween(900,1000)
		price900to1000Products.forEach {
			println("${it.name} - ${it.price}")
		}
		assert(price900to1000Products.size == 1)

		println("4. 가격이 899인 문서 검색")
		val price899Products = productRepository.findByPrice(899)
		price899Products.forEach {
			println("${it.name} - ${it.price}")
		}
		assert(price899Products.size == 1)

		println("5. 가격이 899,1999인 문서 검색")
		val price899or1999Products = productRepository.findByPriceIn(listOf(899,1999))
		price899or1999Products.forEach {
			println("${it.name} - ${it.price}")
		}
		assert(price899or1999Products.size == 2)

		println("ElasticsearchOperations 를 사용한 검색")
		
		println("1. 가격이 1000 이상인 문서 검색")
	    val price1000ProductsQuery = elasticsearchOperations.search(
			NativeQueryBuilder().withQuery { q ->
				q.range { r ->
					r.number { n->
						n.field("price").gte(1000.0)
					}
				}
			}.build(),
		    Product::class.java
	    )
		price1000ProductsQuery.forEach {
		    println("${it.content.name} - ${it.content.price}")
	    }
	    assert(price1000ProductsQuery.count() == 1)
		assertEquals(
			price1000Products.map { it.id },
			price1000ProductsQuery.searchHits.map{it.content.id}
		)

		println("2. 가격이 900 이하인 문서 검색")
		val price900ProductsQuery = elasticsearchOperations.search(
			NativeQueryBuilder().withQuery { q ->
				q.range { r ->
					r.number { n ->
						n.field("price").lte(900.0)
					}
				}
			}.build(),
			Product::class.java
		)
		price900ProductsQuery.forEach {
			println("${it.content.name} - ${it.content.price}")
		}
		assertEquals(
			price900Products.map { it.id },
			price900ProductsQuery.searchHits.map{it.content.id}
		)

		println("3. 가격이 900 이상 1000 이하인 문서 검색")
		val price900to1000ProductsQuery = elasticsearchOperations.search(
			NativeQueryBuilder().withQuery { q ->
				q.range { r ->
					r.number { n ->
						n.field("price").gte(900.0).lte(1000.0)
					}
				}
			}.build(),
			Product::class.java
		)
		assert(price900to1000ProductsQuery.count() == 1)
		assertEquals(
			price900to1000Products.map { it.id },
			price900to1000ProductsQuery.searchHits.map{it.content.id}
		)

		println("4. 가격이 899인 문서 검색")
		val price899ProductsQuery = elasticsearchOperations.search(
			NativeQueryBuilder().withQuery { q ->
				q.term { t -> t.field("price").value(899.0) }
			}.build(),
			Product::class.java
		)
		assert(price899ProductsQuery.count() == 1)
		assertEquals(
			price899Products.map { it.id },
			price899ProductsQuery.searchHits.map{it.content.id}
		)

		println("5. 가격이 899,1999인 문서 검색")
		val price899or1999ProductsQuery = elasticsearchOperations.search(
			NativeQueryBuilder().withQuery {q->

				q.terms{t->
					t.field("price").terms{v->
						v.value(
							listOf(
								FieldValue.of(899),
								FieldValue.of(1999)
							)
						)
					}
				}
			}.build()
			, Product::class.java
		)
		price899or1999ProductsQuery.forEach{
			println("${it.content.name} - ${it.content.price}")
		}
		assertEquals(
			price899or1999Products.map { it.id },
			price899or1999ProductsQuery.searchHits.map{it.content.id}
		)

	}
}