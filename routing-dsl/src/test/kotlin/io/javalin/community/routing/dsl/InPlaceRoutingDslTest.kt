package io.javalin.community.routing.dsl

import io.javalin.Javalin
import io.javalin.community.routing.RouteMethod
import io.javalin.community.routing.dsl.specification.TestSpecification
import io.javalin.testtools.JavalinTest
import io.javalin.testtools.TestConfig
import kong.unirest.Unirest.get
import kong.unirest.Unirest.request
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.ThrowableAssert.ThrowingCallable
import org.junit.jupiter.api.Test

class InPlaceRoutingDslTest : TestSpecification() {

    @Test
    fun `each http dsl method is properly mapped to javalin handler`() = JavalinTest.test(
        // given: a javalin app with routes defined using the dsl
        Javalin.create {
            it.routing {
                before("/before") { header("test", "before") }
                after("/after") { header("test", "after") }
                get("/get") { header("test", "get") }
                post("/post") { header("test", "post") }
                put("/put") { header("test", "put") }
                patch("/patch") { header("test", "patch") }
                delete("/delete") { header("test", "delete") }
                head("/head") { header("test", "head") }
                options("/options") { header("test", "options") }
            }
        },
        defaultConfig
    ) { _, client ->
        // when: a request is made to the http route
        RouteMethod.values()
            .filter { it.isHttpMethod }
            .map { it.name.lowercase() }
            .map { it to request(it, "${client.origin}/$it").asEmpty() }
            .forEach { (method, response) ->
                // then: the response is the same as the route name
                assertThat(response.status).isEqualTo(200)
                assertThat(response.headers.getFirst("test")).isEqualTo(method)
            }

        // when: a request is made to the before handler
        get("${client.origin}/before").asEmpty().also {
            // then: the response is the same as the route name
            assertThat(it.status).isEqualTo(404)
            assertThat(it.headers.getFirst("test")).isEqualTo("before")
        }

        // when: a request is made to the after handler
        get("${client.origin}/after").asEmpty().also {
            // then: the response is the same as the route name
            assertThat(it.status).isEqualTo(404)
            assertThat(it.headers.getFirst("test")).isEqualTo("after")
        }
    }

    @Path("/path/{name}")
    class ValidPath(val name: String)

    @Test
    fun `should properly handle class based routes`() = JavalinTest.test(
        // given: a javalin app with routes defined using the dsl
        Javalin.create { config ->
            config.routing {
                get<ValidPath> { result(it.name) }
            }
        },
        defaultConfig
    ) { _, client ->
        // when: a request is made to the http route with a path parameter
        val response = get("${client.origin}/path/panda").asString()

        // then: the response contains properly mapped path parameter
        assertThat(response.body).isEqualTo("panda")
    }

    class MissingAnnotationPath(val name: String)

    @Test
    fun `should throw exception when class based route is invalid`() {
        // given: a javalin app with routes defined using the dsl
        val app = ThrowingCallable {
            Javalin.create {
                it.routing {
                    // when: a route is defined with path without @Path annotation
                    get<MissingAnnotationPath> { }
                }
            }
        }

        // then: an exception is thrown
        assertThatThrownBy(app)
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("@Path annotation not found")
    }

    @Path("/path/{typo}")
    class InvalidParameterPath(val invalid: String)

    @Test
    fun `should throw exception when path parameter doesn't match constructor parameter`() {
        // given: a javalin app with routes defined using the dsl
        val app = ThrowingCallable {
            Javalin.create {
                it.routing {
                    // when: a route is defined with invalid @Path annotation
                    get<InvalidParameterPath> { }
                }
            }
        }

        // then: an exception is thrown
        assertThatThrownBy(app)
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Path parameter 'invalid' not found in ")
    }


}