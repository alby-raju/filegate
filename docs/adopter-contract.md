# First adopter workflow

The user selected local POSIX storage, inspection only, with AV optional.
This narrows the deployment target without removing planned PNG/JPEG, PDF/DOCX or
mixed-attachment coverage. It does not establish an external adopter or production
approval.

## Decided

- One host application owns authentication, authorization and upload admission.
- Captured originals are inspected; no preparation or derivative is produced.
- Local POSIX private staging and pending-object publication are the first storage
  target. Approved bytes are verified after persistence before atomic no-replace
  publication. Published objects survive gate shutdown.
- PNG/JPEG inspection uses the explicit isolated Linux image check. Unsupported
  formats block until their mandatory inspectors are implemented and installed.
- AV is optional. Installing a scanner must never activate it implicitly. A policy
  that explicitly requires an AV capability must fail compilation if it is absent;
  required-scan outages or uncertainty must block approval. No malware-absence claim
  is made for policies that omit AV.

## Still required before freezing M3.1

- Choose download/private-storage/inline/share use and concrete host response rules.
- Agree deployment limits for input size, staging, pixels, decoded data, worker
  concurrency, memory, CPU, scratch and timeouts. Test values are not defaults.
- Agree the restricted PDF/DOCX acceptance profiles and acceptable rejection rate.
- Obtain a representative adopter corpus, including malformed and unsupported files.
- Validate the actual filesystem, crash durability and ambiguous-commit recovery.

The local example is not a complete web server or an authorization system. The
Linux integration test composes isolated image inspection with verified local
publication and asserts that blocked content creates no public object. Real host
integration and qualification remain M3/M5 work.
