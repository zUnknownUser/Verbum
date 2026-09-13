# Editorial review

`verbum-pipeline review` creates a queue of proposals and a separate decisions JSON file.
All decisions start `pending`, with no reviewer or approval timestamp.

The reviewer reads the queue alongside its source references, checks each claim and its
provenance, and edits **only** the matching decisions: `status` (`approved` or `rejected`),
`reviewer`, `reviewedAt` (ISO 8601 with timezone), and optionally `note`.

Approval applies to the complete batch. Pending/rejected/missing decisions block publication.
To correct or remove a rejected proposal, edit a copy of the imported bundle, normalize it and
prepare a new review under new filenames. Changed content/evidence/license metadata invalidates
the previous review. Referenced entities and sources must remain internally consistent.

These are local editorial records, not authenticated digital signatures. Only the human editor
should supply real approval. Automated tests create synthetic reviewers solely in temporary,
isolated test schemas; they never approve files in this directory.

Fixtures remain development content even after a fixture review. A license field records the
editor's source metadata; the software does not establish that legal permission exists.
