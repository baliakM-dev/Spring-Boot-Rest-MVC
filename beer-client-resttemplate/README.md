# 🍺 Beer Client – RestTemplate (Spring Boot 4)

This module demonstrates a synchronous HTTP client built using RestTemplate in Spring Boot 4.
It consumes the beer-store REST API and forwards responses (including RFC 7807 ProblemDetail errors) in a clean and structured way.

## 🚀 Purpose

This project demonstrates:
- Usage of RestTemplate (legacy synchronous HTTP client)
- Root URI configuration 
- Timeout configuration 
- Pagination handling with generics 
- Downstream error forwarding using ProblemDetail 
- Clean layered architecture (Controller → Service → HTTP Client) 
- Resilience patterns (CircuitBreaker + Retry via Resilience4j)

```
beer-client-resttemplate
│
├── controller/
│   └── BeerController
│
├── service/
│   └── BeerRestTemplateService
│
├── config/
│   └── Resilience4jConfig
│   └── RestTemplateConfig
│
├── dto/
│   ├── BeerCreateRequestDTO
│   ├── BeerPatchRequestDTO
│   ├── BeerResponseDTO
│   ├── BeerUpdateRequestDTO
│   ├── CategoriesDTO
│   └── PageResponse
│
└── exceptions/
    └── BeerAlreadyExistsException
    └── BeerNotFoundException
    └── BeerValidationException
    └── ServiceUnavailableException
```

## 🧩 Technology Stack 
- Java 25	
- Spring Boot 4	
- Spring Web MVC	
- RestTemplate	
- Jackson 3 (tools.jackson)	
- RFC 7807 ProblemDetail
- Resilience4j (CircuitBreaker, Retry, RateLimiter)

## 🏁 Why RestTemplate?

Although in maintenance mode, RestTemplate is still widely used in enterprise applications. 

This module demonstrates:	
- Legacy client integration	
- Proper timeout configuration	
- Generic type deserialization	
- Downstream error forwarding

## 🔮 Next Steps
Other client implementations in this project:	
- beer-client-restclient – Modern synchronous HTTP client (Spring 6+)	
- beer-client-httpinterface – Declarative client using @HttpExchange

### 👤 Author

Ing. Martin Baliak
Junior Backend Developer – Spring Boot
