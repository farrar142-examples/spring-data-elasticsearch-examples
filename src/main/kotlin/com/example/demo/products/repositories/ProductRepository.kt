package com.example.demo.products.repositories

import com.example.demo.products.documents.Product
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository

interface ProductRepository : ElasticsearchRepository<Product, String>