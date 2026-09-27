package app.recipefork.server

import app.recipefork.server.account.AccountStore
import app.recipefork.server.account.KeySetSeal
import app.recipefork.server.account.accountRoutes
import app.recipefork.server.attestation.Attestation
import app.recipefork.server.attestation.AttestationService
import app.recipefork.server.attestation.NoAttestation
import app.recipefork.server.db.Database
import app.recipefork.server.db.DatabaseConfig
import app.recipefork.server.dictionary.DictionaryStore
import app.recipefork.server.dictionary.dictionaryRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import java.time.Clock
import javax.sql.DataSource

fun main() {
    configuredServer(System.getenv()).start(wait = true)
}

/**
 * The server as configured by [env], not yet started. Its configuration is read before the store is
 * reached ([connect]), so a server that cannot run — `KEY_SET_SECRET` missing, blank or malformed —
 * refuses to start with a message naming what is wrong, as does an `ATTESTATION` it does not know
 * ([Attestation.fromEnvironment]).
 */
internal fun configuredServer(
    env: Map<String, String>,
    connect: (DatabaseConfig) -> DataSource = Database::connect,
): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> {
    val seal = KeySetSeal.fromEnvironment(env)
    val clock = Clock.systemUTC()
    val attestation = Attestation.fromEnvironment(env, clock)
    val port = env["PORT"]?.toIntOrNull() ?: 8080
    val store = connect(DatabaseConfig.fromEnvironment(env))
    return embeddedServer(Netty, port = port, module = { module(store, seal, attestation, clock) })
}

/** The server's composition root without a store: content negotiation only. */
fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
}

/** The server's composition root over the migrated [store], without a key set secret: the dictionaries only. */
fun Application.module(store: DataSource) {
    module()
    routing {
        dictionaryRoutes(DictionaryStore(store))
    }
}

/**
 * The server's composition root: each feature's routes over the migrated [store]; issued key sets
 * are sealed by [seal] (`KEY_SET_SECRET`, see [KeySetSeal.fromEnvironment]); device verdicts come from
 * [attestation] (none by default, so every account created is muted), and the month from [clock].
 */
fun Application.module(
    store: DataSource,
    seal: KeySetSeal,
    attestation: AttestationService = NoAttestation,
    clock: Clock = Clock.systemUTC(),
) {
    module(store)
    routing {
        accountRoutes(AccountStore(store), seal = seal, attestation = attestation, clock = clock)
    }
}
