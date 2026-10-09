# Company letters

Company admins configure **Letter management > Company & HR details** once for all three letter types.

## Setup

Enter the legal company name, business address, HR contact email, HR signatory name and title, and signature image. Phone, website, identifiers, and company logo are optional. Images remain limited to PNG/JPEG, 1 MB, and 3000 pixels per side.

Employment verification, travel, and vacation letters share this identity. Their standard wording matches the earlier letter implementation; separate template editors, type-specific profile selectors, and wording controls are removed. All three letter types become available once required company and HR fields are complete. Incomplete setup may be saved.

Existing company configuration is presented as one shared company/HR identity. Standard wording is restored for future letters. Existing issued text, images, and PDFs remain frozen.

## Review

Admins confirm the employee name, title, and employment start date. Corrections require an explanation. Shared company and HR details are shown as a summary. Admins preview and verify the final PDF before approval; changing employee details invalidates that preview.

The server retains company access checks, configuration and request revisions, and the prohibition on self-approval. Signature assets are excluded from employee draft responses and PDFs. Approval stores final text, resolved identity, and PDF together.

## Compatibility

The company-scoped `/letter-settings` API retains its existing configuration contract internally, while presenting one shared identity and centrally maintained wording. Letter availability and request routes retain company scoping. Historical approved PDFs are unchanged.
