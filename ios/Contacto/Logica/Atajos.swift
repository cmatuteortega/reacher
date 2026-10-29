import UIKit

/// El atajo del icono (mantener pulsado), como `atajos.xml` de Android:
/// "Anadir persona" abre la agenda. Se publica al arrancar, con los textos ya
/// traducidos, y se atiende por el Router como un aviso mas.
@MainActor
enum Atajos {
    static let anadir = "com.cmatuteortega.contacto.anadir"

    static func publicar() {
        guard !Entorno.enPruebas else { return }
        UIApplication.shared.shortcutItems = [
            UIApplicationShortcutItem(
                type: anadir,
                localizedTitle: t("anadir_persona"),
                localizedSubtitle: t("anadir_persona_largo"),
                icon: UIApplicationShortcutIcon(type: .add),
                userInfo: nil
            ),
        ]
    }

    @discardableResult
    static func atender(_ atajo: UIApplicationShortcutItem) -> Bool {
        guard atajo.type == anadir else { return false }
        Entorno.shared.router.pendiente = .anadir
        return true
    }
}

/// SwiftUI no entrega los atajos del icono: hace falta el delegado de UIKit.
@MainActor
final class DelegadoApp: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        Atajos.publicar()
        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        // Arranque en frio desde el atajo: la escena aun no existe para recibirlo.
        if let atajo = options.shortcutItem { Atajos.atender(atajo) }
        let configuracion = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuracion.delegateClass = DelegadoEscena.self
        return configuracion
    }
}

/// Con la app ya abierta (o en segundo plano) el atajo llega aqui.
@MainActor
final class DelegadoEscena: NSObject, UIWindowSceneDelegate {
    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(Atajos.atender(shortcutItem))
    }
}
