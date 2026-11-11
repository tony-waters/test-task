# Package the code by use case, around a small core that depends on nothing

The first packages were named after the data (`patient`, `action`). As the use cases arrived, each package filled up with several unrelated jobs. `patient` held 20 classes covering the entity, list search, detail, CSV export and two controllers. `patient` and `action` imported each other, and query objects imported exceptions from `web`. To understand any one package, you had to read most of the others.

We now package the code by use case. Each package in `patients/` is one thing the app does: `list`, `detail`, `timeline`, `export`, plus the `quality` rules that `detail` and `timeline` share. A package holds that use case's request parsing, read models, controllers and its own small Spring Data repository (`extends Repository<…>`, declaring only the queries it uses). Below the use cases sit two packages:

- `core` holds the entities, their enums and a minimal `PatientRepository`.
- `shared` holds `London` and the request-error types.

Neither imports anything above it. Dependencies point one way (`export → list`, `detail → timeline → quality`, every use case → `core` → `shared`), and an ArchUnit test enforces that direction and the absence of cycles. `dashboard`, `importer`, `security` and `web` keep their places: `web` holds only cross-cutting request concerns.

## Considered Options

- **Package by layer** (`controller` / `service` / `repository`): rejected. It spreads each use case over three packages and still produces large mixed packages.
- **Layers, then aggregate, then kind** (`domain/`, `application/<aggregate>/command|query|event`, `web/<aggregate>/dto`), as in another project of ours: rejected for this app. It suits a write-heavy app with one aggregate. This app is read-only, with about six separate read use cases, so nearly everything would land in one `application/patient/query` package.
- **All queries in one repository per entity**: rejected. `ActionRepository` would collect timeline, engagement, dashboard and data-quality queries. `core` would then have to import every use case's read models, bringing back the cycle. Each use case owns its queries instead, and Spring Data allows several repository interfaces per entity.
- **Hexagonal (ports and adapters)**: rejected as overkill. It adds interfaces and adapters to hide a database the app only reads.
- **Spring Modulith for the boundary check**: not chosen. It treats nested packages as one module's internals, so it can't express the rules between the use cases inside `patients/`. ArchUnit can.

## Consequences

- The two patient controllers split into one per use case: `PatientListPageController`, `PatientListApiController`, `PatientDetailPageController`, `PatientDetailApiController`, `TimelineApiController` and `PatientExportController`. That means more files, but each use case owns its endpoints.
- Classes used only inside a use case become package-private, so each package's `public` types are its interface to the others.
- JPQL constructor expressions name classes in full (`select new uk.bit1...`), so moving a read model means updating those strings by hand.
- `dashboard` already fits the pattern. If it grows, it splits the same way.
