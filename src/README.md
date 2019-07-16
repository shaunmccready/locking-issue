
# Apache Curator and ZooKeeper Test

### *Trying to solve the issue im experiencing with KeeperException$NoNodeException: KeeperErrorCode = NoNode

Running Curator 4.0.1 and on ZooKeeper server version 3.5.5 , obtained [HERE](http://apache.forsale.plus/zookeeper/zookeeper-3.5.5/)
because this combination worked, ie: This combination didnt generate UNIMPLEMENTED errors as mentioned [HERE](https://stackoverflow.com/questions/35734590/apache-curator-unimplemented-errors-when-trying-to-create-znodes)
Also need the bin version or will get ClassNotFoundExceptions and won't start ZooKeeper. 


Would be nice to log the value of the String variable ourPath in the class LockInternals.java(L:225) 
which would include both the path being passed to Curator, as well as the UUID being generated but im not sure if its possible
to do that besides debugging and copying the value to clipboard. Then to follow the internalLockLoop() method  to see how
driver.getsTheLock() is able to get the lock on one thread meanwhile another thread seems to still have possession of the lock, as indicated in the logging in the console 
when running the test.


Please email me at shaunwmccready@gmail.com for any questions or comments. Thank you!

 