# ADR: Customer File Association

## Context

`biz_file` currently stores folder, scope, uploader, and storage metadata, but it has no customer relation. The mobile file center therefore cannot safely expose "files for this customer" from a customer detail page. A client-side keyword or folder filter would not prove customer ownership and could show unrelated files.

## Decision

Add a separate relation table instead of putting `customer_id` directly on `biz_file`:

- `biz_customer_file(id, customer_id, file_id, relation_type, created_by, created_at, deleted)`
- Unique active relation on `(customer_id, file_id, relation_type)`
- Foreign-key behavior follows the project's existing migration policy; cleanup is handled explicitly by the service layer so historical files remain recoverable.
- `relation_type` starts with `primary` and `attachment`; new types require a permission review.

The relation is an access narrowing filter, not a new permission grant. A user may see a related file only when both checks pass:

1. The existing file authorization allows `file:view` and the file/folder ACL permits access.
2. The existing customer data-scope service allows access to the referenced customer.

Upload and attach are separate operations. Upload keeps the existing file permission checks; attaching an existing file additionally requires customer edit/attachment authority and a customer-scope check. The API must reject a relation to an inaccessible customer or file with `403`, never silently create a relation.

## API Shape

- `GET /api/customer/{customerId}/files`: paginated related files, reusing file authorization and customer scope.
- `POST /api/customer/{customerId}/files/{fileId}`: attach an existing file; idempotent for the same active relation.
- `DELETE /api/customer/{customerId}/files/{fileId}`: remove the relation only; it must not delete the underlying file.

The mobile customer detail page may show the entry only after the backend returns the capability. It must not infer customer-file access from `file:view` alone.

## Verification Gates

- Migration test verifies indexes, uniqueness, and nullable historical data behavior.
- Service tests cover same-scope access, cross-scope `403`, inaccessible file `403`, idempotent attach, and relation removal without file deletion.
- PC and mobile contract checks must use the same three endpoints and response envelope.
- VS Code Problems/SonarLint diagnostics and the existing permission/data-scope checks remain required before handoff.
