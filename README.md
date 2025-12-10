1. Elasticsearch 설치

```Dockerfile
FROM  elasticsearch:9.2.2
RUN bin/elasticsearch-plugin install --batch analysis-nori
```

```yml
services:
  elasticsearch:
    image: elasticsearch:nori
    build:
      context: .
      dockerfile: Dockerfile
    container_name: elasticsearch
    ports:
      - "9200:9200"
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
```

2. application.yml 설정

```yml
spring:
  application:
    name: demo
  elasticsearch:
    uris:
      - http://localhost:9200
```

3. ElasticsearchConfig 설정

```kotlin
// src/main/kotlin/com/example/demo/globals/configs/ElasticsearchConfig.kt
@Configuration
@EnableElasticsearchRepositories(basePackages = ["com.example.demo"])
class ElasticsearchConfig
```

### @EnableElasticsearchRepositories란?

`@EnableElasticsearchRepositories`는 Spring Data Elasticsearch의 Repository 인터페이스를 자동으로 스캔하고 구현체를 생성해주는 어노테이션입니다.

**왜 필요한가?**

Spring Data JPA를 사용할 때 `@EnableJpaRepositories`가 자동 설정되는 것과 달리, **Elasticsearch는 명시적으로 활성화**해줘야 합니다. 이 어노테이션이 없으면
`ElasticsearchRepository`를 상속받은 인터페이스가 빈(Bean)으로 등록되지 않아 의존성 주입 시 오류가 발생합니다.

**주요 속성:**

| 속성                   | 설명                     | 예시                         |
|----------------------|------------------------|----------------------------|
| `basePackages`       | Repository를 스캔할 패키지 경로 | `["com.example.demo"]`     |
| `basePackageClasses` | 스캔 기준이 될 클래스           | `ProductRepository::class` |
| `excludeFilters`     | 스캔에서 제외할 필터            | -                          |

> **참고**: `basePackages`를 지정하지 않으면 해당 설정 클래스가 위치한 패키지와 그 하위 패키지를 스캔합니다.
