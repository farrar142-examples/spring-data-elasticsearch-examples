package com.example.demo.products.repositories

import com.example.demo.products.documents.Product
import org.springframework.data.domain.Sort
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository
import java.time.LocalDateTime
import java.util.Date

interface ProductRepository : ElasticsearchRepository<Product, String> {
    fun findByName(name: String): List<Product>
    fun findByNameIn(names: List<String>): List<Product>

	fun findByPrice(price: Long): List<Product>
	fun findByPriceGreaterThanEqual(price: Long): List<Product>
	fun findByPriceLessThanEqual(price: Long): List<Product>
	fun findByPriceBetween(minPrice: Long, maxPrice: Long): List<Product>
	fun findByPriceIn(prices: List<Long>): List<Product>

	fun findByCreatedAtBetween(start: LocalDateTime, end: LocalDateTime): List<Product>
	fun findByCreatedAtBefore(end: LocalDateTime): List<Product>
	fun findByCreatedAtAfter(start: LocalDateTime): List<Product>

	fun findByAvailable(available: Boolean): List<Product>

	fun findByAvailableOrderByPriceAsc(available: Boolean): List<Product>
	fun findByAvailableOrderByPriceDesc(available: Boolean): List<Product>
	// 정렬 관련 메서드
	fun findAllByOrderByPriceAsc(): List<Product>
	fun findAllByOrderByPriceDesc(): List<Product>
	fun findByAvailable(available: Boolean, sort: Sort): List<Product>
}