package app.recipefork.server

import app.recipefork.server.account.AccountStore
import app.recipefork.server.account.KeySetSeal
import app.recipefork.server.account.accountRoutes
import app.recipefork.server.db.Database
import app.recipefork.server.db.DatabaseConfig
import app.recipefork.server.dictionary.DictionaryStore
import app.recipefork.server.dictionary.dictionaryRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import javax.sql.DataSource

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val store = Database.connect(DatabaseConfig.fromEnvironment())
    val seal = KeySetSeal.fromEnvironment()
    embeddedServer(Netty, port = port, module = { module(store, seal) }).start(wait = true)
}

/** The server's composition root without a store: content negotiation only. */
fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
}

/**
 * The server's composition root: each feature's routes over the migrated [store]; issued key sets
 * are sealed by [seal] (`KEY_SET_SECRET`, else a secret for this process only).
 */
fun Application.module(store: DataSource, seal: KeySetSeal = KeySetSeal.fromEnvironment()) {
    module()
    routing {
        dictionaryRoutes(DictionaryStore(store))
        accountRoutes(AccountStore(store), seal = seal)
    }
}
