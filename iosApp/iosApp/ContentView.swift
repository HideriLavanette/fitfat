import SwiftUI
import UIKit
import Fitfat

/// Весь интерфейс — Compose Multiplatform из фреймворка Fitfat (app/src/commonMain). Swift здесь только оболочка.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea() // отступы под строку состояния и «чёлку» считает сам Compose
    }
}
