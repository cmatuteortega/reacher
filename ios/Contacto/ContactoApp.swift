import SwiftData
import SwiftUI
import UIKit

@main
struct ContactoApp: App {
    @UIApplicationDelegateAdaptor(DelegadoApp.self) private var delegado
    private let entorno = Entorno.shared

    init() {
        Refresco.registrar()
    }

    var body: some Scene {
        WindowGroup {
            RaizView(router: entorno.router)
                .environmentObject(entorno.ajustes)
                .modelContainer(entorno.contenedor)
        }
    }
}
