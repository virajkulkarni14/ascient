package us.kesslern.ascient

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import mu.KotlinLogging
import org.junit.Test
import org.junit.jupiter.api.TestInstance
import kotlin.contracts.ExperimentalContracts
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExperimentalContracts
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SessionTests {
    private val logger = KotlinLogging.logger {}

    @Test
    fun `create and use a session`() {
        var sessionId: String
        authenticateUser("admin", "password") {
            assertEquals(HttpStatusCode.OK, status)
            sessionId = (readJson(content) as AuthenticationResponse).sessionId
        }
        logger.debug("Using session $sessionId")
        request(HttpMethod.Get, "/api/booleans", authenticated = false, sessionId = sessionId) {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun `test empty session`() {
        request(HttpMethod.Get, "/api/booleans", authenticated = false, sessionId = "") {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun `test unknown user`() {
        authenticateUser("baduser", "password") {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }

    @Test
    fun `test bad password`() {
        authenticateUser("admin", "foobar") {
            assertEquals(HttpStatusCode.Unauthorized, status)
        }
    }
}
