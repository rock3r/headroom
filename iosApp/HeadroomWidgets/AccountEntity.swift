import AppIntents

/// An account a widget can show, from the snapshot the app wrote.
struct AccountEntity: AppEntity {
    let id: String
    let name: String

    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Account"
    static let defaultQuery = AccountQuery()

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(name)")
    }
}

struct AccountQuery: EntityQuery {
    func entities(for identifiers: [AccountEntity.ID]) async throws -> [AccountEntity] {
        let accounts = WidgetSnapshot.load().accounts
        return identifiers.compactMap { id in
            accounts.first { $0.id == id }.map { AccountEntity(id: $0.id, name: $0.name) }
        }
    }

    func suggestedEntities() async throws -> [AccountEntity] {
        WidgetSnapshot.load().accounts.map { AccountEntity(id: $0.id, name: $0.name) }
    }
}
