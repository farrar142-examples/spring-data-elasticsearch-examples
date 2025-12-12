package com.example.demo

import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQueryBuilders
import co.elastic.clients.elasticsearch.core.search.CompletionSuggester
import co.elastic.clients.elasticsearch.core.search.FieldSuggester
import co.elastic.clients.elasticsearch.core.search.Suggester
import co.elastic.clients.json.JsonData
import com.example.demo.products.documents.Product
import com.example.demo.products.repositories.ProductRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.web.format.DateTimeFormatters
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.suggest.Completion
import org.springframework.data.elasticsearch.core.suggest.response.CompletionSuggestion
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalUnit
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

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
                createdAt = LocalDateTime.now().minusDays(1 ),
                available = true
            ),
            Product(
                name = "Samsung Galaxy S21",
                description = "Flagship smartphone from Samsung with great performance",
                price = 899,
                category = "Electronics",
                stock = 150,
                createdAt = LocalDateTime.now().minusWeeks(1),
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
	@Test
	fun `Date 필드 검색 예제`(){
		println("ElasticsearchRepository 를 사용한 검색")

		println("1. 생성일자가 3일 이내인 문서 검색")
		val recentProducts = productRepository.findByCreatedAtAfter(LocalDateTime.now().minusDays(3))
		recentProducts.forEach {
			println("${it.name} - ${it.createdAt}")
		}
		assert(recentProducts.size == 2)

		println("2. 생성일자가 5일 이전인 문서 검색")
		val olderProducts = productRepository.findByCreatedAtBefore(LocalDateTime.now().minusDays(5))
		olderProducts.forEach {
			println("${it.name} - ${it.createdAt}")
		}
		assert(olderProducts.size == 1)

		println("3. 생성시간이 12시간 에서 36시간 사이인 문서 검색")
		val betweenProducts = productRepository.findByCreatedAtBetween(
			LocalDateTime.now().minusHours(36),
			LocalDateTime.now().minusHours(12)
		)
		betweenProducts.forEach {
			println("${it.name} - ${it.createdAt}")
		}
		assert(betweenProducts.size == 1)

		println("ElasticsearchOperations 를 사용한 검색")
		println("1. 생성일자가 3일 이내인 문서 검색(DateMath사용)")
		val recentProductsQuery = elasticsearchOperations.search(
			NativeQueryBuilder().withQuery { q ->
				q.range { r ->
					r.date{d->
						d.field("createdAt")
							.gte("now-3d"
							)
					}
				}
			}.build(),
			Product::class.java
		)
		recentProductsQuery.forEach {
			println("${it.content.name} - ${it.content.createdAt}")
		}
		assertEquals(
			recentProducts.map { it.id },
			recentProductsQuery.searchHits.map{it.content.id}
		)

		println("2. 생성일자가 5일 이전인 문서 검색(Formatter로 정확한 날짜 지정)")
		val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
		val olderProductsQuery = elasticsearchOperations.search(
			NativeQueryBuilder().withQuery { q ->
				q.range { r ->
					r.date { d->
						d.field("createdAt")
							.lte(LocalDateTime.now().minusDays(5).format(formatter))

					}
				}
			}.build(),
			Product::class.java
		)
		olderProductsQuery.forEach {
			println("${it.content.name} - ${it.content.createdAt}")
		}
		assertEquals(
			olderProducts.map { it.id },
			olderProductsQuery.searchHits.map{it.content.id}
		)


		println("3. 생성시간이 12시간 에서 36시간 사이인 문서 검색")
		val betweenProductsQuery = elasticsearchOperations.search(
			NativeQueryBuilder().withQuery { q ->
				q.range { r ->
					r.date { d->
						d.field("createdAt")
							.gte("now-36h")
							.lte("now-12h")
					}
				}
			}.build(),
			Product::class.java
		)
		betweenProductsQuery.forEach {
			println("${it.content.name} - ${it.content.createdAt}")
		}
		assertEquals(
			betweenProducts.map { it.id },
			betweenProductsQuery.searchHits.map{it.content.id}
		)
	}

	@Test
	fun `Bool 필드 검색 예제`(){
		println("ElasticsearchRepository 를 사용한 검색")

		println("1.판매 가능한 문서 검색")
		val availableProducts = productRepository.findByAvailable(true)
		availableProducts.forEach {
			println("${it.name} - ${it.stock} - ${it.available}")
		}
		assert(availableProducts.size == 3)

		println("2. 판매 불가능한 문서 검색")
		val unavailableProducts = productRepository.findByAvailable(false)
		unavailableProducts.forEach {
			println("${it.name} - ${it.stock} - ${it.available}")
		}
		assert(unavailableProducts.isEmpty())

		println("ElasticsearchOperations 를 사용한 검색")

		println("1. 재고가 100개 이상이고 판매 가능한 문서 검색")
		val availableProductsQuery = elasticsearchOperations.search(
			NativeQueryBuilder().withQuery { q ->
				q.bool { b ->
					b.must { m -> m.term { t -> t.field("available").value(true) } }
				}
			}.build(),
			Product::class.java
		)
		availableProductsQuery.forEach {
			println("${it.content.name} - ${it.content.stock} - ${it.content.available}")
		}
		assertEquals(
			availableProducts.map { it.id },
			availableProductsQuery.searchHits.map{it.content.id}
		)

		println("2. 판매 불가능한 문서 검색")
		val unavailableProductsQuery = elasticsearchOperations.search(
			NativeQueryBuilder().withQuery { q ->
				q.bool { b ->
					b.mustNot { m -> m.term { t -> t.field("available").value(true) } }
				}
			}.build(),
			Product::class.java
		)
		unavailableProductsQuery.forEach {
			println("${it.content.name} - ${it.content.stock} - ${it.content.available}")
		}
		assertEquals(
			unavailableProducts.map { it.id },
			unavailableProductsQuery.searchHits.map{it.content.id}
		)
		println("3. 그외의 조합으로 판매가능한 문서 검색")

		println("3-1, filter 절에 match 쿼리 사용")
		val availableProductsQueryWithFilterMatch = elasticsearchOperations.search(
			NativeQueryBuilder().withQuery { q ->
				q.bool { b ->
					b.filter { f -> f.match { t -> t.field("available").query(true) } }
				}
			}.build(),
			Product::class.java
		)
		availableProductsQueryWithFilterMatch.forEach {
			println("${it.content.name} - ${it.content.stock} - ${it.content.available}")
		}
		assertEquals(
			availableProducts.map { it.id },
			availableProductsQueryWithFilterMatch.searchHits.map{it.content.id}
		)
		println("3-2 , filter 절에 term 쿼리 사용")
		val availableProductsQueryWithFilterTerm = elasticsearchOperations.search(
			NativeQueryBuilder().withQuery { q ->
				q.bool { b ->
					b.filter { f -> f.term { t -> t.field("available").value(true) } }
				}
			}.build(),
			Product::class.java
		)
		availableProductsQueryWithFilterTerm.forEach {
			println("${it.content.name} - ${it.content.stock} - ${it.content.available}")
		}
		assertEquals(
			availableProducts.map { it.id },
			availableProductsQueryWithFilterTerm.searchHits.map{it.content.id}
		)
		println("3-3 , must 절에 match 쿼리 사용")
		val availableProductsQueryWithMustMatch = elasticsearchOperations.search(
			NativeQueryBuilder().withQuery { q ->
				q.bool { b ->
					b.must { m -> m.match { t -> t.field("available").query(true) } }
				}
			}.build(),
			Product::class.java
		)
		availableProductsQueryWithMustMatch.forEach {
			println("${it.content.name} - ${it.content.stock} - ${it.content.available}")
		}
		assertEquals(
			availableProducts.map { it.id },
			availableProductsQueryWithMustMatch.searchHits.map{it.content.id}
		)

		println("4. 그외의 필드에 대해서도 Bool 쿼리 작성 가능")
		println("4-1, 900 이상인 상품을 검색하되, 카테고리가 Computers인 상품을 우선 정렬")
		val complexBoolQuery = elasticsearchOperations.search(
			NativeQueryBuilder().withQuery { q ->
				q.bool { b ->
					b.must { m -> m.range { r -> r.number { n->n.field("price").gte(950.0) }} }
					b.should { m -> m.match { mt->mt.field("category").query("Computers") } }
				}
			}.build(),
			Product::class.java
		)
		complexBoolQuery.forEach {
			println("${it.content.name} - ${it.content.stock} - ${it.content.price}")
		}
		assertEquals(complexBoolQuery.searchHits.count(),2)
		assertEquals(complexBoolQuery.searchHits.first().content.category,"Computers")
		assertEquals(complexBoolQuery.searchHits.last().content.category,"Electronics")
	}

	@Test
	fun `Sort를 사용한 정렬 예제`() {
		println("=== ElasticsearchRepository를 사용한 정렬 ===")

		println("1. 가격 오름차순 정렬")
		val priceAscProducts = productRepository.findAllByOrderByPriceAsc()
		priceAscProducts.forEach {
			println("${it.name} - ${it.price}")
		}
		assertEquals(3, priceAscProducts.size)
		assertEquals(899, priceAscProducts.first().price)
		assertEquals(1999, priceAscProducts.last().price)

		println("2. 가격 내림차순 정렬")
		val priceDescProducts = productRepository.findAllByOrderByPriceDesc()
		priceDescProducts.forEach {
			println("${it.name} - ${it.price}")
		}
		assertEquals(3, priceDescProducts.size)
		assertEquals(1999, priceDescProducts.first().price)
		assertEquals(899, priceDescProducts.last().price)

		println("3. 복합 정렬 (카테고리 오름차순 + 가격 내림차순)")
		val multiSort = productRepository.findByAvailable(
			true,
			Sort.by(Sort.Direction.ASC, "category").and(Sort.by(Sort.Direction.DESC, "price"))
		)
		multiSort.forEach {
			println("${it.name} - ${it.category} - ${it.price}")
		}
		assertEquals(3, multiSort.size)
		assertEquals("Computers", multiSort.first().category)

		println("\n=== ElasticsearchOperations를 사용한 정렬 ===")

		println("1. 가격 오름차순 정렬")
		val priceAscQuery = elasticsearchOperations.search(
			NativeQueryBuilder()
				.withQuery { q -> q.matchAll { it } }
				.withSort(Sort.by(Sort.Direction.ASC, "price"))
				.build(),
			Product::class.java
		)
		priceAscQuery.forEach {
			println("${it.content.name} - ${it.content.price}")
		}
		assertEquals(3, priceAscQuery.searchHits.size)
		assertEquals(
			priceAscProducts.map { it.id },
			priceAscQuery.searchHits.map { it.content.id }
		)

		println("2. 가격 내림차순 정렬")
		val priceDescQuery = elasticsearchOperations.search(
			NativeQueryBuilder()
				.withQuery { q -> q.matchAll { it } }
				.withSort(Sort.by(Sort.Direction.DESC, "price"))
				.build(),
			Product::class.java
		)
		priceDescQuery.forEach {
			println("${it.content.name} - ${it.content.price}")
		}
		assertEquals(3, priceDescQuery.searchHits.size)
		assertEquals(
			priceDescProducts.map { it.id },
			priceDescQuery.searchHits.map { it.content.id }
		)

		println("3. 복합 정렬 (카테고리 오름차순 + 가격 내림차순)")
		val multiSortQuery = elasticsearchOperations.search(
			NativeQueryBuilder()
				.withQuery { q -> q.term { t -> t.field("available").value(true) } }
				.withSort(Sort.by(Sort.Direction.ASC, "category").and(Sort.by(Sort.Direction.DESC, "price")))
				.build(),
			Product::class.java
		)
		multiSortQuery.forEach {
			println("${it.content.name} - ${it.content.category} - ${it.content.price}")
		}
		assertEquals(3, multiSortQuery.searchHits.size)
		assertEquals(
			multiSort.map { it.id },
			multiSortQuery.searchHits.map { it.content.id }
		)
	}

	@Test
	fun `Pagination을 사용한 페이징 예제`() {
		// 테스트용 추가 데이터 생성
		listOf(
			Product(
				name = "Sony Headphones",
				description = "High quality wireless headphones",
				price = 299,
				category = "Electronics",
				stock = 200,
				createdAt = LocalDateTime.now().minusDays(2),
				available = true
			),
			Product(
				name = "LG Monitor",
				description = "27 inch 4K monitor",
				price = 499,
				category = "Electronics",
				stock = 80,
				createdAt = LocalDateTime.now().minusDays(3),
				available = true
			)
		).let(productRepository::saveAll)

		println("=== ElasticsearchRepository를 사용한 페이징 ===")

		println("1. 기본 페이징 (첫 번째 페이지, 2개씩)")
		val page0 = productRepository.findAllBy(PageRequest.of(0, 2))
		page0.content.forEach {
			println("${it.name} - ${it.price}")
		}
		assertEquals(2, page0.content.size)
		assertEquals(5, page0.totalElements)
		assertEquals(3, page0.totalPages)
		assertEquals(0, page0.number)
		assertEquals(true, page0.hasNext())

		println("2. 두 번째 페이지")
		val page1 = productRepository.findAllBy(PageRequest.of(1, 2))
		page1.content.forEach {
			println("${it.name} - ${it.price}")
		}
		assertEquals(2, page1.content.size)
		assertEquals(1, page1.number)
		assertEquals(true, page1.hasNext())

		println("3. 마지막 페이지")
		val page2 = productRepository.findAllBy(PageRequest.of(2, 2))
		page2.content.forEach {
			println("${it.name} - ${it.price}")
		}
		assertEquals(1, page2.content.size)
		assertEquals(2, page2.number)
		assertEquals(false, page2.hasNext())

		println("4. 페이징 + 정렬 (가격 오름차순)")
		val pageWithSort = productRepository.findAllBy(PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "price")))
		pageWithSort.content.forEach {
			println("${it.name} - ${it.price}")
		}
		assertEquals(2, pageWithSort.content.size)
		assertEquals(299, pageWithSort.content.first().price)

		println("5. 조건 + 페이징 (available=true, 2개씩)")
		val pageWithCondition = productRepository.findByAvailable(true, PageRequest.of(0, 2))
		pageWithCondition.content.forEach {
			println("${it.name} - ${it.available}")
		}
		assertEquals(2, pageWithCondition.content.size)
		assertEquals(5, pageWithCondition.totalElements)

		println("\n=== ElasticsearchOperations를 사용한 페이징 ===")

		println("1. 기본 페이징 (첫 번째 페이지, 2개씩)")
		val page0Query = elasticsearchOperations.search(
			NativeQueryBuilder()
				.withQuery { q -> q.matchAll { it } }
				.withPageable(PageRequest.of(0, 2))
				.build(),
			Product::class.java
		)
		page0Query.searchHits.forEach {
			println("${it.content.name} - ${it.content.price}")
		}
		assertEquals(2, page0Query.searchHits.size)
		assertEquals(5, page0Query.totalHits)
		assertEquals(
			page0.content.map { it.id },
			page0Query.searchHits.map { it.content.id }
		)

		println("2. 두 번째 페이지")
		val page1Query = elasticsearchOperations.search(
			NativeQueryBuilder()
				.withQuery { q -> q.matchAll { it } }
				.withPageable(PageRequest.of(1, 2))
				.build(),
			Product::class.java
		)
		page1Query.searchHits.forEach {
			println("${it.content.name} - ${it.content.price}")
		}
		assertEquals(2, page1Query.searchHits.size)
		assertEquals(
			page1.content.map { it.id },
			page1Query.searchHits.map { it.content.id }
		)

		println("3. 마지막 페이지")
		val page2Query = elasticsearchOperations.search(
			NativeQueryBuilder()
				.withQuery { q -> q.matchAll { it } }
				.withPageable(PageRequest.of(2, 2))
				.build(),
			Product::class.java
		)
		page2Query.searchHits.forEach {
			println("${it.content.name} - ${it.content.price}")
		}
		assertEquals(1, page2Query.searchHits.size)
		assertEquals(
			page2.content.map { it.id },
			page2Query.searchHits.map { it.content.id }
		)

		println("4. 페이징 + 정렬 (가격 오름차순)")
		val pageWithSortQuery = elasticsearchOperations.search(
			NativeQueryBuilder()
				.withQuery { q -> q.matchAll { it } }
				.withPageable(PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "price")))
				.build(),
			Product::class.java
		)
		pageWithSortQuery.searchHits.forEach {
			println("${it.content.name} - ${it.content.price}")
		}
		assertEquals(2, pageWithSortQuery.searchHits.size)
		assertEquals(
			pageWithSort.content.map { it.id },
			pageWithSortQuery.searchHits.map { it.content.id }
		)

		println("5. 조건 + 페이징 (available=true, 2개씩)")
		val pageWithConditionQuery = elasticsearchOperations.search(
			NativeQueryBuilder()
				.withQuery { q -> q.term { t -> t.field("available").value(true) } }
				.withPageable(PageRequest.of(0, 2))
				.build(),
			Product::class.java
		)
		pageWithConditionQuery.searchHits.forEach {
			println("${it.content.name} - ${it.content.available}")
		}
		assertEquals(2, pageWithConditionQuery.searchHits.size)
		assertEquals(5, pageWithConditionQuery.totalHits)
		assertEquals(
			pageWithCondition.content.map { it.id },
			pageWithConditionQuery.searchHits.map { it.content.id }
		)

		println("6. Search After를 사용한 페이징 (Deep Pagination 해결)")

		println("1. 첫 번째 페이지 조회 (정렬 필수)")
		val firstPageQuery = elasticsearchOperations.search(
			NativeQueryBuilder()
				.withQuery { q -> q.matchAll { it } }
				.withSort(Sort.by(Sort.Direction.ASC, "price"))
				.withPageable(PageRequest.of(0, 2))
				.build(),
			Product::class.java
		)
		firstPageQuery.searchHits.forEach {
			println("${it.content.name} - ${it.content.price} - sortValues: ${it.sortValues}")
		}
		assertEquals(2, firstPageQuery.searchHits.size)

		// 마지막 문서의 sortValues 가져오기
		val lastSortValues = firstPageQuery.searchHits.last().sortValues
		println("마지막 문서의 sortValues: $lastSortValues")

		println("2. Search After로 두 번째 페이지 조회")
		val secondPageQuery = elasticsearchOperations.search(
			NativeQueryBuilder()
				.withQuery { q -> q.matchAll { it } }
				.withSort(Sort.by(Sort.Direction.ASC, "price"))
				.withSearchAfter(lastSortValues)
				.withPageable(PageRequest.of(0, 2))
				.build(),
			Product::class.java
		)
		secondPageQuery.searchHits.forEach {
			println("${it.content.name} - ${it.content.price} - sortValues: ${it.sortValues}")
		}
		assertEquals(2, secondPageQuery.searchHits.size)
		// 두 번째 페이지의 첫 번째 가격이 첫 번째 페이지의 마지막 가격보다 커야 함
		assert(secondPageQuery.searchHits.first().content.price > firstPageQuery.searchHits.last().content.price)

		// 두 번째 페이지의 마지막 sortValues로 세 번째 페이지 조회
		val secondLastSortValues = secondPageQuery.searchHits.last().sortValues

		println("3. Search After로 세 번째(마지막) 페이지 조회")
		val thirdPageQuery = elasticsearchOperations.search(
			NativeQueryBuilder()
				.withQuery { q -> q.matchAll { it } }
				.withSort(Sort.by(Sort.Direction.ASC, "price"))
				.withSearchAfter(secondLastSortValues)
				.withPageable(PageRequest.of(0, 2))
				.build(),
			Product::class.java
		)
		thirdPageQuery.searchHits.forEach {
			println("${it.content.name} - ${it.content.price} - sortValues: ${it.sortValues}")
		}
		assertEquals(1, thirdPageQuery.searchHits.size)
	}

	@Test
	fun `Aggregation을 사용한 집계 예제`() {
		// 테스트용 추가 데이터 생성
		listOf(
			Product(
				name = "Sony Headphones",
				description = "High quality wireless headphones",
				price = 299,
				category = "Electronics",
				stock = 200,
				createdAt = LocalDateTime.now().minusDays(2),
				available = true
			),
			Product(
				name = "LG Monitor",
				description = "27 inch 4K monitor",
				price = 499,
				category = "Computers",
				stock = 80,
				createdAt = LocalDateTime.now().minusDays(3),
				available = false
			)
		).let(productRepository::saveAll)

		println("=== ElasticsearchRepository는 Aggregation을 직접 지원하지 않음 ===")
		println("Aggregation은 ElasticsearchOperations를 사용해야 합니다.")

		println("1. Terms Aggregation - 카테고리별 문서 수")
		val termsAggQuery = NativeQueryBuilder()
			.withQuery { q -> q.matchAll { it } }
			.withAggregation("category_agg", Aggregation.of { a ->
				a.terms { t -> t.field("category") }
			})
			.withMaxResults(0) // 집계만 필요할 때 문서는 가져오지 않음
			.build()

		val termsAggResult = elasticsearchOperations.search(termsAggQuery, Product::class.java)
		val termsAggregations = termsAggResult.aggregations as ElasticsearchAggregations
		val categoryAgg = termsAggregations.get("category_agg") as ElasticsearchAggregation
		val categoryBuckets = categoryAgg.aggregation().aggregate.sterms().buckets().array()

		categoryBuckets.forEach { bucket ->
			println("카테고리: ${bucket.key().stringValue()}, 문서 수: ${bucket.docCount()}")
		}
		assertEquals(2, categoryBuckets.size)

		println("2. Stats Aggregation - 가격 통계 (min, max, avg, sum, count)")
		val statsAggQuery = NativeQueryBuilder()
			.withQuery { q -> q.matchAll { it } }
			.withAggregation("price_stats", Aggregation.of { a ->
				a.stats { s -> s.field("price") }
			})
			.withMaxResults(0)
			.build()

		val statsAggResult = elasticsearchOperations.search(statsAggQuery, Product::class.java)
		val statsAggregations = statsAggResult.aggregations as ElasticsearchAggregations
		val priceStatsAgg = statsAggregations.get("price_stats") as ElasticsearchAggregation
		val priceStats = priceStatsAgg.aggregation().aggregate.stats()

		println("가격 통계:")
		println("  - 최소값: ${priceStats.min()}")
		println("  - 최대값: ${priceStats.max()}")
		println("  - 평균값: ${priceStats.avg()}")
		println("  - 합계: ${priceStats.sum()}")
		println("  - 개수: ${priceStats.count()}")
		assertEquals(5, priceStats.count())
		assertEquals(299.0, priceStats.min())
		assertEquals(1999.0, priceStats.max())

		println("3. Range Aggregation - 가격대별 문서 수")
		val rangeAggQuery = NativeQueryBuilder()
			.withQuery { q -> q.matchAll { it } }
			.withAggregation("price_ranges", Aggregation.of { a ->
				a.range { r ->
					r.field("price")
						.ranges(
							AggregationRange.of { ar -> ar.to(500.0).key("0-500") },
							AggregationRange.of { ar -> ar.from(500.0).to(1000.0).key("500-1000") },
							AggregationRange.of { ar -> ar.from(1000.0).key("1000+") }
						)
				}
			})
			.withMaxResults(0)
			.build()

		val rangeAggResult = elasticsearchOperations.search(rangeAggQuery, Product::class.java)
		val rangeAggregations = rangeAggResult.aggregations as ElasticsearchAggregations
		val priceRangeAgg = rangeAggregations.get("price_ranges") as ElasticsearchAggregation
		val rangeBuckets = priceRangeAgg.aggregation().aggregate.range().buckets().array()

		rangeBuckets.forEach { bucket ->
			println("가격대 ${bucket.key()}: ${bucket.docCount()}개")
		}
		assertEquals(3, rangeBuckets.size)

		println("4. Avg Aggregation - 카테고리별 평균 가격 (Sub-Aggregation)")
		val subAggQuery = NativeQueryBuilder()
			.withQuery { q -> q.matchAll { it } }
			.withAggregation("category_avg_price", Aggregation.of { a ->
				a.terms { t -> t.field("category") }
					.aggregations(mapOf(
						"avg_price" to Aggregation.of { sub -> sub.avg { avg -> avg.field("price") } }
					))
			})
			.withMaxResults(0)
			.build()

		val subAggResult = elasticsearchOperations.search(subAggQuery, Product::class.java)
		val subAggregations = subAggResult.aggregations as ElasticsearchAggregations
		val categoryAvgAgg = subAggregations.get("category_avg_price") as ElasticsearchAggregation
		val categoryAvgBuckets = categoryAvgAgg.aggregation().aggregate.sterms().buckets().array()

		categoryAvgBuckets.forEach { bucket ->
			val avgPrice = bucket.aggregations()["avg_price"]?.avg()?.value()
			println("카테고리: ${bucket.key().stringValue()}, 평균 가격: $avgPrice")
		}

		println("5. Filter Aggregation - 판매 가능한 상품만 집계")
		val filterAggQuery = NativeQueryBuilder()
			.withQuery { q -> q.matchAll { it } }
			.withAggregation("available_products", Aggregation.of { a ->
				a.filter { f -> f.term { t -> t.field("available").value(true) } }
					.aggregations(mapOf(
						"avg_price" to Aggregation.of { sub -> sub.avg { avg -> avg.field("price") } },
						"category_count" to Aggregation.of { sub -> sub.terms { t -> t.field("category") } }
					))
			})
			.withMaxResults(0)
			.build()

		val filterAggResult = elasticsearchOperations.search(filterAggQuery, Product::class.java)
		val filterAggregations = filterAggResult.aggregations as ElasticsearchAggregations
		val availableAgg = filterAggregations.get("available_products") as ElasticsearchAggregation
		val filterResult = availableAgg.aggregation().aggregate.filter()

		println("판매 가능한 상품 수: ${filterResult.docCount()}")
		println("판매 가능한 상품 평균 가격: ${filterResult.aggregations()["avg_price"]?.avg()?.value()}")

		val availableCategoryBuckets = filterResult.aggregations()["category_count"]?.sterms()?.buckets()?.array()
		availableCategoryBuckets?.forEach { bucket ->
			println("  - ${bucket.key().stringValue()}: ${bucket.docCount()}개")
		}

		println("6. Date Histogram Aggregation - 일별 생성 문서 수")
		val dateHistogramQuery = NativeQueryBuilder()
			.withQuery { q -> q.matchAll { it } }
			.withAggregation("daily_count", Aggregation.of { a ->
				a.dateHistogram { dh ->
					dh.field("createdAt")
						.calendarInterval(CalendarInterval.Day)
				}
			})
			.withMaxResults(0)
			.build()

		val dateHistogramResult = elasticsearchOperations.search(dateHistogramQuery, Product::class.java)
		val dateHistogramAggregations = dateHistogramResult.aggregations as ElasticsearchAggregations
		val dailyAgg = dateHistogramAggregations.get("daily_count") as ElasticsearchAggregation
		val dailyBuckets = dailyAgg.aggregation().aggregate.dateHistogram().buckets().array()

		dailyBuckets.forEach { bucket ->
			println("날짜: ${bucket.keyAsString()}, 문서 수: ${bucket.docCount()}")
		}
	}

	@Test
	fun `Completion필드를 사용한 검색 예제`(){
		val products = listOf(
			Product(
				name = "삼성 갤럭시 S23",
				description = "삼성의 최신 플래그십 스마트폰",
				price = 950,
				category = "Electronics",
				stock = 120,
				createdAt = LocalDateTime.now().minusDays(2),
				available = true,
				suggests = Completion(listOf("삼성 갤럭시 s23", "갤럭시 s23", "samsung", "galaxy", "s23", "삼성"))
			),
			Product(
				name = "LG 그램 16",
				description = "휴대성이 뛰어난 고성능 노트북",
				price = 1800,
				category = "Computers",
				stock = 40,
				createdAt = LocalDateTime.now().minusWeeks(1),
				available = true,
				suggests = Completion(listOf("lg 그램 16", "그램 16", "lg", "gram 16"))
			),
			Product(
				name = "삼성 QLED TV 55인치",
				description = "선명한 화질의 55인치 QLED TV",
				price = 1200,
				category = "Electronics",
				stock = 25,
				createdAt = LocalDateTime.now().minusMonths(1),
				available = true,
				suggests = Completion(listOf("삼성 qled", "qled tv", "55인치", "qled", "samsung qled"))
			),
			Product(
				name = "LG OLED TV 65인치",
				description = "완벽한 블랙 표현의 65인치 OLED TV",
				price = 2500,
				category = "Electronics",
				stock = 10,
				createdAt = LocalDateTime.now().minusDays(10),
				available = true,
				suggests = Completion(listOf("lg oled", "oled tv", "65인치", "lg oled 65"))
			),
			Product(
				name = "현대 아반떼 스마트키",
				description = "현대 아반떼 전용 스마트키 정품 액세서리",
				price = 150,
				category = "Automotive",
				stock = 200,
				createdAt = LocalDateTime.now().minusDays(30),
				available = true,
				suggests = Completion(listOf("현대", "아반떼", "스마트키", "hyundai", "avante"))
			),
			Product(
				name = "카카오 프렌즈 인형",
				description = "카카오 프렌즈 캐릭터 인형",
				price = 35,
				category = "Toys",
				stock = 300,
				createdAt = LocalDateTime.now().minusDays(5),
				available = true,
				suggests = Completion(listOf("카카오", "카카오프렌즈", "프렌즈", "인형", "kakao"))
			),
			Product(
				name = "제주 감귤 1kg",
				description = "신선한 제주산 감귤 1kg 팩",
				price = 12,
				category = "Food",
				stock = 500,
				createdAt = LocalDateTime.now().minusDays(3),
				available = true,
				suggests = Completion(listOf("제주", "감귤", "제주 감귤", "1kg"))
			),
			Product(
				name = "오뚜기 진라면 매운맛",
				description = "한국의 대표적인 라면 브랜드",
				price = 2,
				category = "Food",
				stock = 1000,
				createdAt = LocalDateTime.now().minusDays(60),
				available = true,
				suggests = Completion(listOf("오뚜기", "진라면", "라면", "ottogi"))
			),
			Product(
				name = "쿠쿠 전기압력밥솥 10인용",
				description = "간편한 요리를 위한 전기압력밥솥",
				price = 220,
				category = "Appliances",
				stock = 80,
				createdAt = LocalDateTime.now().minusWeeks(2),
				available = true,
				suggests = Completion(listOf("쿠쿠", "전기압력밥솥", "밥솥", "cuckoo"))
			),
			Product(
			 name = "한샘 책상 L형",
			 description = "실용적인 L형 책상 가구",
			 price = 180,
			 category = "Furniture",
			 stock = 60,
			 createdAt = LocalDateTime.now().minusDays(7),
			 available = true,
			 suggests = Completion(listOf("한샘", "책상", "L형", "한샘 책상"))
			),
			Product(
				name = "네이버 스마트홈 AI 스피커",
				description = "음성으로 집을 제어하는 스마트 스피커",
				price = 120,
				category = "Electronics",
				stock = 250,
				createdAt = LocalDateTime.now().minusDays(4),
				available = true,
				suggests = Completion(listOf("네이버", "스마트홈", "ai 스피커", "네이버 클로바"))
			),
			Product(
				name = "삼성 무선청소기 제트",
				description = "강력한 흡입력의 무선청소기",
				price = 450,
				category = "Appliances",
				stock = 90,
				createdAt = LocalDateTime.now().minusWeeks(3),
				available = true,
				suggests = Completion(listOf("삼성 청소기", "무선청소기", "제트", "samsung jet"))
			),
			Product(
				name = "농심 신라면",
				description = "매콤한 맛의 한국 전통 라면",
				price = 1,
				category = "Food",
				stock = 2000,
				createdAt = LocalDateTime.now().minusDays(20),
				available = true,
				suggests = Completion(listOf("농심", "신라면", "라면", "shin ramyun"))
			),
			Product(
				name = "LG 냉장고 500L",
				description = "대용량 500리터 냉장고",
				price = 1400,
				category = "Appliances",
				stock = 15,
				createdAt = LocalDateTime.now().minusMonths(2),
				available = true,
				suggests = Completion(listOf("lg 냉장고", "500L", "냉장고", "lg refrigerator"))
			),
			Product(
				name = "한샘 의자 오피스",
				description = "장시간 작업에 편안한 사무용 의자",
				price = 120,
				category = "Furniture",
				stock = 70,
				createdAt = LocalDateTime.now().minusDays(12),
				available = true,
				suggests = Completion(listOf("한샘 의자", "오피스 체어", "의자"))
			),
			Product(
				name = "마켓컬리 유기농 바나나 1kg",
				description = "신선한 수입 유기농 바나나 1kg",
				price = 8,
				category = "Food",
				stock = 180,
				createdAt = LocalDateTime.now().minusDays(2),
				available = true,
				suggests = Completion(listOf("유기농", "바나나", "마켓컬리", "1kg"))
			),
			Product(
				name = "삼성 SSD 1TB",
				description = "빠른 속도의 내부 SSD 저장장치",
				price = 150,
				category = "Computers",
				stock = 120,
				createdAt = LocalDateTime.now().minusWeeks(4),
				available = true,
				suggests = Completion(listOf("ssd 1tb", "삼성 ssd", "samsung ssd", "저장장치"))
			),
			Product(
				name = "ASUS 게이밍 노트북",
				description = "고성능 GPU 탑재 게이밍 노트북",
				price = 2200,
				category = "Computers",
				stock = 30,
				createdAt = LocalDateTime.now().minusDays(15),
				available = true,
				suggests = Completion(listOf("asus", "게이밍 노트북", "rog", "gaming laptop"))
			),
			Product(
				name = "미미박스 화장품 세트",
				description = "여성을 위한 스킨케어 화장품 세트",
				price = 45,
				category = "Beauty",
				stock = 260,
				createdAt = LocalDateTime.now().minusDays(6),
				available = true,
				suggests = Completion(listOf("화장품", "미미박스", "스킨케어", "세트"))
			),
			Product(
				name = "토이저러스 블록 세트",
				description = "어린이용 블록 세트 (조립 장난감)",
				price = 55,
				category = "Toys",
				stock = 150,
				createdAt = LocalDateTime.now().minusWeeks(1),
				available = true,
				suggests = Completion(listOf("블록", "토이저러스", "장난감", "블록 세트"))
			),
			Product(
				name = "김치찌개",
				description = "돈골을 푹 고아 끓인 김치찌개",
				price = 55,
				category = "Foods",
				stock = 150,
				createdAt = LocalDateTime.now().minusWeeks(1),
				available = true,
				suggests = Completion(listOf("김치찌개"))
			)
		)
		// 테스트용으로 저장
		productRepository.saveAll(products)
        val suggester = Suggester.of { s ->
            s.suggesters("prod-suggest") { fs ->
                fs.prefix("김치찍")
                    .completion { c ->
                        c.field("suggests")
                            .size(5)
                            .skipDuplicates(true)
							.fuzzy{f->
								f.transpositions(true)
							}
                    }
            }
        }
		val query = NativeQueryBuilder()
				.withSuggester(suggester)
				.withMaxResults(0)
				.build()
		val suggestResult = elasticsearchOperations.search(query,Product::class.java)
		val r = suggestResult.suggest
			?.suggestions
			?.filterIsInstance<CompletionSuggestion<Product>>()
			?.flatMap{cs->
				cs.entries.flatMap { e->
					e.options.mapNotNull{o->
						o.searchHit?.content
					}
				}
			}?:emptyList()
		r.forEach {
			println("${it.name} - ${it.price}")
		}
	}

	@Test
	fun `Synonyms를 이용한 동의어 검색 예제`(){
		val product = Product(
			name = "김치찌개",
			description = "김치찌개",
			price = 999,
			category = "Foods",
			stock = 150,
			createdAt = LocalDateTime.now().minusDays(1),
			available = true
		).let(productRepository::save)
		val synonymNameQuery = elasticsearchOperations.search(
			NativeQueryBuilder().withQuery { q ->
				q.match { m ->
					m.field("name")
						.query("kimchi stew")
				}
			}.build(),
			Product::class.java
		)
		synonymNameQuery.forEach {
			println("${it.content.name} - ${it.content.description}")
		}
		assertTrue(synonymNameQuery.searchHits.isNotEmpty())
		val synonymDescriptionQuery = elasticsearchOperations.search(
			NativeQueryBuilder().withQuery { q ->
				q.match { m ->
					m.field("description")
						.query("kimchi stew")
				}
			}.build(),
			Product::class.java
		)
		synonymDescriptionQuery.forEach{
			println("${it.content.name} - ${it.content.description}")
		}
		assertTrue(synonymDescriptionQuery.searchHits.isEmpty())
	}
}