# Console design notes

Subject: an operator's view of the order saga. Audience: engineers running the demo (and interviewers
watching). Primary job: place an order, then watch it move through stock and payment to a final state.

Metaphor: air-traffic flight progress strips. Each order is a paper strip in a rack; the strip's
cells fill in as the saga advances, and its holder colour says where it stands.

Tokens
- rack      #D9DFE3  board the strips sit in
- paper     #F7F9F6  strip surface
- ink       #1D2A33  text
- holding   #E3A92F  in flight (pending / stock held / cancelling)
- cleared   #2E8A67  confirmed
- returned  #B0445B  cancelled after compensation
- refused   #7B8893  rejected
- signal    #1F5FAD  focus + primary action

Type: Barlow Condensed (strip data, headings) + Barlow (controls, body). Left-aligned throughout.

Rules
- Boldness lives in the strip. Everything else is quiet.
- The only unprompted motion: a new strip slides into the rack. Off under prefers-reduced-motion.
- Step cells are a real sequence, so they're shown in order; no other numbering.

Revision from first draft: started with a dark "mission control" board with a single bright accent,
which is a stock look. Switched to a light rack with paper strips, which matches the metaphor and
reads well in a lit room or on a projector.
