# Reference

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.1/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.0.1/maven-plugin/build-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.0.1/reference/web/servlet.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.0.1/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Spring Boot Actuator](https://docs.spring.io/spring-boot/4.0.1/reference/actuator/index.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Building a RESTful Web Service with Spring Boot Actuator](https://spring.io/guides/gs/actuator-service/)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.

## Environment

| Variable | Default | Purpose |
|---|---|---|
| `MARKETDATA_YAHOO_ENABLED` | `true` | Kill switch for the Yahoo quote provider. Turning it off does not break the quote path — cached last-known prices are still served, labelled stale. |
| `MARKETDATA_YAHOO_DAILY_BUDGET` | `2000` | Self-imposed politeness cap, **not** a vendor-published limit. Yahoo does not sanction this access, so we choose a ceiling rather than discover theirs. |
| `MARKETDATA_QUOTE_REFRESH_CRON` | `0 30 22 * * *` | Nightly sweep that warms demand-registered symbols, after the US close. |
| `marketdata.retention.tracked-symbol-days` | `90` | Retires symbols nobody has asked about in this long. |
| `ALPHAVANTAGE_API_KEY` | — | The durable app-level provider key. The `keys.html` field is an in-memory **session override** that does not survive a restart (this service spins down when idle on its hosting plan). |
