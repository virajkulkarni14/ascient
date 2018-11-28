
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.Application
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.call
import io.ktor.client.engine.apache.Apache
import io.ktor.client.response.readText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.Test
import org.junit.jupiter.api.TestInstance
import org.slf4j.LoggerFactory
import org.testcontainers.containers.PostgreSQLContainer
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


class KPostgreSQLContainer : PostgreSQLContainer<KPostgreSQLContainer>()

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AscientTests {

    private val logger = LoggerFactory.getLogger(AscientTests::class.java)
    private val mapper = ObjectMapper().registerModule(KotlinModule())

    private val databaseDriver = "org.postgresql.Driver"
    private val databaseConnection = "jdbc:tc:postgresql:9.6.8://hostname/databasename?TC_DAEMON=true"

    private val client = HttpClient(Apache)
    private var backend = System.getProperty("ascient.backend")
    private var useRealBackend = !backend.isEmpty()

    init {
        logger.info("Starting...")

        if (!useRealBackend) {
            KPostgreSQLContainer().start()
            Flyway
                    .configure()
                    .dataSource(databaseConnection, "", "")
                    .load()
                    .migrate()
            Database.connect(databaseConnection, databaseDriver)
        }
    }

    @Test
    fun `test boolean CRUD operations`() {
        // insert new boolean with random UUID as name and record the new ID
        val name = UUID.randomUUID()
        val newId = with(request(
            HttpMethod.Post,
            "/api/booleans?name=$name&value=${true}")) {
            assertEquals(HttpStatusCode.OK, status)
            val content = content
            assertNotNull(content)
            content.toInt()
        }

        // verify boolean inserted with value and ID
        with(request(HttpMethod.Get, "/api/booleans/$newId")) {
            val newBoolean = mapper.readValue(content, BooleanDBO::class.java)
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(true, newBoolean.value)
        }

        // change value to false
        with(request(HttpMethod.Put, "/api/booleans/$newId?value=${false}")) {
            assertEquals(HttpStatusCode.NoContent, status)
        }

        // get all values and verify
        with(request(HttpMethod.Get, "/api/booleans")) {
            val content = content
            assertNotNull(content)
            val newBooleans: List<BooleanDBO> = mapper.readValue(content)
            assertEquals(HttpStatusCode.OK, status)
            val newValue = newBooleans.find { it.id == newId }?.value
            assertEquals(false, newValue)
        }

        // delete
        with(request(HttpMethod.Delete, "/api/booleans/$newId")) {
            assertEquals(HttpStatusCode.NoContent, status)
        }

        // get all values and verify
        with(request(HttpMethod.Get, "/api/booleans")) {
            val newBooleans: List<BooleanDBO> = mapper.readValue(content ?: throw RuntimeException())
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(null, newBooleans.find { it.id == newId })
        }
    }

    @Test
    fun `test default parameters`() {
        // POST without a value
        val id = with(request(HttpMethod.Post, "/api/booleans?name=${UUID.randomUUID()}")) {
            assertEquals(HttpStatusCode.OK, status)
            content?.toInt()
        }

        // Verify inserted with value True
        with(request(HttpMethod.Get, "/api/booleans/$id")) {
            val newBoolean = mapper.readValue(content, BooleanDBO::class.java)
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(true, newBoolean.value)
        }
    }

    @Test
    fun `test bad parameters`() {
        with(request(HttpMethod.Get, "/api/booleans/9999999")) {
            assertEquals(HttpStatusCode.BadRequest, status)
        }

        with(request(HttpMethod.Get, "/api/booleans/-1")) {
            assertEquals(HttpStatusCode.BadRequest, status)
         }

            with(request(HttpMethod.Post, "/api/booleans")) {
            assertEquals("Missing parameter: name", content)
            assertEquals(HttpStatusCode.BadRequest, status)
        }

        val id = with(request(HttpMethod.Post, "/api/booleans?name=${UUID.randomUUID()}")) {
            assertEquals(HttpStatusCode.OK, status)
            content?.toInt()
        }

            with(request(HttpMethod.Put, "/api/booleans/$id")) {
            assertEquals("Missing parameter: value", content)
            assertEquals(HttpStatusCode.BadRequest, status)
        }
    }

    private fun request(method: HttpMethod, uri: String): UnifiedResponse {
        if (useRealBackend) {
            return runBlocking {
                with(requestWithBackend(method, backend + uri)) {
                    UnifiedResponse(response.status, response.readText())
                }
            }
        } else {
            with(requestWithMockKtor(method, uri)) {
                return UnifiedResponse(response.status(), response.content)
            }
        }
    }

    private fun requestWithMockKtor(
            method: HttpMethod,
            uri: String
    ): TestApplicationCall =
            withTestApplication(Application::server) {
                handleRequest(method, uri)
            }

    private suspend fun requestWithBackend(
            method: HttpMethod,
            uri: String
    ): HttpClientCall = client.call(uri) {
        this.method = method
    }
}

data class UnifiedResponse(
        val status: HttpStatusCode?,
        val content: String?
)