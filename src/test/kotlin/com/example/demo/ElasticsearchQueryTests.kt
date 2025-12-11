package com.example.demo

import co.elastic.clients.elasticsearch._types.FieldValue
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange
import co.elastic.clients.elasticsearch._types.aggregations.CalendarInterval
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
import org.springframework.boot.autoconfigure.web.format.DateTimeFormatters
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations
import org.springframework.data.elasticsearch.client.elc.NativeQuery
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalUnit
import kotlin.test.assertContains
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
}