# 2. Printing is done with an atomic native call

A print job was three calls on a global connection, and two quick taps could close each other's connection. An optional `macAddress` is added to the print calls so the whole job is one native unit in a queue. A queue only in Dart was discarded because other instances or isolates would skip it. The three-step API stays so nobody breaks.

Related: #1
