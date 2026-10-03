# The serverless dictionary ships with the application

**Status:** accepted, adds a serverless variant to DOM-014; DOM-014 as written stands for the server

**Context:** DOM-014 keeps both dictionaries in the product's own store, maintained
by hand by whoever operates it, and a device takes a new copy when it next reaches
the server. In serverless mode there is no server to reach (FLOW-061). For v0.14
the user asked the lead to fill the dictionary "по своему усмотрению", covering as
many recipes as possible, and decided it is kept in the repository and shipped in
the application.

**Decision:**
- In serverless mode both dictionaries are maintained in the product's source and
  shipped with the application.
- A device's copy changes only with an application update.
- Entry ids are permanent there as everywhere (DOM): an entry is marked removed,
  never deleted.

**Consequences:** the dictionary is as fresh as the installed application. When a server exists,
DOM-014 applies and the shipped copy is only the starting one.

**Alternatives considered:**
- *Download the dictionary from a static address.* Rejected: it is a server under
  another name, and FLOW-061 promises none.
