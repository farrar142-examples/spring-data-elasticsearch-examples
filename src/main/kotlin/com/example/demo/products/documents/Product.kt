package com.example.demo.products.documents

import org.springframework.data.annotation.Id
import org.springframework.data.elasticsearch.annotations.CompletionField
import org.springframework.data.elasticsearch.annotations.DateFormat
import org.springframework.data.elasticsearch.annotations.Document
import org.springframework.data.elasticsearch.annotations.Field
import org.springframework.data.elasticsearch.annotations.FieldType
import org.springframework.data.elasticsearch.annotations.Setting
import org.springframework.data.elasticsearch.core.suggest.Completion
import java.time.LocalDateTime


@Document(indexName = "products")
@Setting(settingPath = "elasticsearch/settings.json")
data class Product(
    @Field(type = FieldType.Text, analyzer = "nori", searchAnalyzer = "synonym_analyzer")
    val name: String,

    @Field(type = FieldType.Text, analyzer = "nori")
    val description: String,

    @Field(type = FieldType.Keyword)
    val category: String,

    @Field(type = FieldType.Long)
    val price: Long,

    @Field(type = FieldType.Integer)
    val stock: Int,

    @Field(type = FieldType.Date, format = [DateFormat.date_hour_minute_second])
    val createdAt: LocalDateTime,

    @Field(type = FieldType.Boolean)
    val available: Boolean,
	@CompletionField(searchAnalyzer ="autocomplete_index_analyzer" )
	val suggests: Completion = Completion(listOf(name.lowercase())),

    @Id
    val id: String? = null,

    )