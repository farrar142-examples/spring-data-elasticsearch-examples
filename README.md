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