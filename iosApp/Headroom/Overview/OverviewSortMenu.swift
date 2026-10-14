import SwiftUI

/// How the overview orders the accounts, as the Android sort control.
struct OverviewSortMenu: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        Menu("Sort accounts", systemImage: "arrow.up.arrow.down") {
            Picker("Sort accounts", selection: sortBinding) {
                Text("Your order").tag("yourOrder")
                Text(model.showsLeft ? "Least left first" : "Most used first").tag("mostUsedFirst")
                Text(model.showsLeft ? "Most left first" : "Least used first").tag("leastUsedFirst")
                Text("Soonest reset first").tag("soonestResetFirst")
                Text("Latest reset first").tag("latestResetFirst")
            }
        }
    }

    private var sortBinding: Binding<String> {
        Binding(
            get: { model.overview?.sort ?? "yourOrder" },
            set: { model.headroom.settings.setOverviewSort(id: $0) }
        )
    }
}
