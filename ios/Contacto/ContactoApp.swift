import SwiftData
import SwiftUI

@main
struct ContactoApp: App {
    private let entorno = Entorno.shared

    var body: some Scene {
        WindowGroup {
            RaizView(router: entorno.router)
                .environmentObject(entorno.ajustes)
                .modelContainer(entorno.contenedor)
        }
    }
}
