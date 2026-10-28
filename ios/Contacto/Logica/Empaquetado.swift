import Foundation

/// Un puesto en el lienzo de las burbujas: centro (relativo al centro de todo) y radio.
struct PuestoBurbuja {
    let centro: CGPoint
    let radio: CGFloat
}

enum Empaquetado {
    static func radio(urgencia: Double, maximo: CGFloat) -> CGFloat {
        let minimo = maximo * 0.4
        return minimo + (maximo - minimo) * CGFloat(min(max(urgencia, 0), 1.2) / 1.2)
    }

    /// Coloca circulos de los `radios` dados (el primero, el mas urgente, en el centro) sin solaparse y
    /// dentro de `ancho`: si no caben, encoge todos hasta que quepan.
    static func colocar(radios: [CGFloat], ancho: CGFloat, hueco: CGFloat = 8) -> (puestos: [PuestoBurbuja], alto: CGFloat) {
        guard !radios.isEmpty else { return ([], 0) }
        var escala: CGFloat = 1
        for _ in 0..<8 {
            let puestos = empaquetar(radios.map { $0 * escala }, hueco: hueco)
            let minX = puestos.map { $0.centro.x - $0.radio }.min() ?? 0
            let maxX = puestos.map { $0.centro.x + $0.radio }.max() ?? 0
            let minY = puestos.map { $0.centro.y - $0.radio }.min() ?? 0
            let maxY = puestos.map { $0.centro.y + $0.radio }.max() ?? 0
            if maxX - minX <= max(ancho, 1) || escala < 0.3 {
                // Centrado en el medio de la caja que ocupan.
                let cx = (minX + maxX) / 2, cy = (minY + maxY) / 2
                return (puestos.map { PuestoBurbuja(centro: CGPoint(x: $0.centro.x - cx, y: $0.centro.y - cy), radio: $0.radio) }, maxY - minY)
            }
            escala *= 0.88
        }
        return (empaquetar(radios.map { $0 * escala }, hueco: hueco), 0)
    }

    /// Cada circulo en el primer sitio libre de una espiral que sale del centro.
    static func empaquetar(_ radios: [CGFloat], hueco: CGFloat) -> [PuestoBurbuja] {
        var puestos: [PuestoBurbuja] = []
        for r in radios {
            if puestos.isEmpty {
                puestos.append(PuestoBurbuja(centro: .zero, radio: r))
                continue
            }
            var angulo = 0.0
            var distancia = CGFloat(0)
            var colocado = false
            var intentos = 0
            while !colocado && intentos < 20000 {
                let c = CGPoint(x: distancia * CGFloat(cos(angulo)), y: distancia * CGFloat(sin(angulo)) * 0.85)
                if puestos.allSatisfy({ hypot($0.centro.x - c.x, $0.centro.y - c.y) >= $0.radio + r + hueco }) {
                    puestos.append(PuestoBurbuja(centro: c, radio: r))
                    colocado = true
                } else {
                    angulo += 0.35
                    distancia += 0.9
                    intentos += 1
                }
            }
            if !colocado { puestos.append(PuestoBurbuja(centro: CGPoint(x: distancia, y: 0), radio: r)) }
        }
        return puestos
    }
}
