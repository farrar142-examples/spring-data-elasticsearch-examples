package com.example.demo.products.repositories

import com.example.demo.products.documents.Product
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository

interface ProductRepository : ElasticsearchRepository<Product, String> {
    fun findByName(name: String): List<Product>
    fun findByNameIn(names: List<String>): List<Product>
	fun findByPrice(price: Long): List<Product>
	fun findByPriceGreaterThanEqual(price: Long): List<Product>
	fun findByPriceLessThanEqual(price: Long): List<Product>
	fun findByPriceBetween(minPrice: Long, maxPrice: Long): List<Product>
	fun findByPriceIn(prices: List<Long>): List<Product>
}