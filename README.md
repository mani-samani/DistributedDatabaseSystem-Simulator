# DistributedDatabaseSimulator

To start, you must use a local MySQL database. There is the JDBC driver for that included in this project.<br/>
Run the CreateDatabase.sql script to set up the DB.<br/>
<br/>
To set up a simulation change the parameters in the file: params.txt<br/>
<br/>
It MUST be in this format:<br/>
<br/>
LOGGING:False<br/>
SEED:65<br/>
Topology:HyperCube<br/>
NumPages:400<br/>
ArrivalRate:350<br/>
DDP:AgentDeadlockDetectionProtocol<br/>
DRP:PriorityDeadlockresolution<br/>
PP:EarliestDeadlineFirst<br/>
DetectionInterval:2000<br/>
MaxActiveTransactions:30<br/>
AgentsHistoryLength:3<br/>
UpdateRate:0.25<br/>
<br/>
This will run 2 simulations, one with AgentDeadlockDetectionProtocol and one with TimeoutDeadlockDetection.<br/>
Use commas to separate variations.<br/>
<br/>
The results will be inserted into a local MySQL database .
