# Glossary

**Job**:
One complete print, from opening the connection to confirming and closing it.
_Avoid_: call, command

**Atomic call**:
The way of printing that receives `macAddress` and does the whole job in one call.
_Avoid_: simple mode

**Completion confirmation**:
The `OUT` answer that says the printer processed everything it was sent.
_Avoid_: ACK, printed

**Not confirmed**:
A timeout after everything was sent: the printer did not confirm it processed the job, and it may still have printed.
_Avoid_: failed
