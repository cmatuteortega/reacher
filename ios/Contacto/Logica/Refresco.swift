import BackgroundTasks
import Foundation

/// Lo mas parecido al trabajo diario de Android que deja iOS: una tarea de
/// actualizacion en segundo plano que, cuando el sistema quiere (suele ser
/// cuando el telefono carga o se usa a menudo la app), rehace los avisos
/// pendientes. Sin ella, los avisos programados solo duran los 7 dias desde
/// la ultima vez que se abrio la app. El sistema decide cuando; no hay hora fija.
enum Refresco {
    static let identificador = "com.cmatuteortega.contacto.refresco"

    /// Antes de que termine el arranque de la app (la declaracion esta en Info.plist, en project.yml).
    static func registrar() {
        guard !Entorno.enPruebas else { return }
        BGTaskScheduler.shared.register(forTaskWithIdentifier: identificador, using: nil) { tarea in
            let trabajo = Task { @MainActor in
                programar()
                await Avisos.shared.reprogramarAhora()
                tarea.setTaskCompleted(success: !Task.isCancelled)
            }
            tarea.expirationHandler = { trabajo.cancel() }
        }
    }

    /// Pide la proxima, no antes de dentro de unas horas. Se llama al pasar a segundo plano y al ejecutarse una.
    static func programar() {
        guard !Entorno.enPruebas else { return }
        let peticion = BGAppRefreshTaskRequest(identifier: identificador)
        peticion.earliestBeginDate = Date().addingTimeInterval(6 * 3600)
        try? BGTaskScheduler.shared.submit(peticion)
    }
}
