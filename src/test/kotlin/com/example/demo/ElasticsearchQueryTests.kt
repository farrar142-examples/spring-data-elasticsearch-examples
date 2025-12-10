package com.example.demo

import com.example.demo.products.documents.Product
import com.example.demo.products.repositories.ProductRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
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

}