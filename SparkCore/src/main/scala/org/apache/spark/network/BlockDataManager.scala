/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.network

import org.apache.spark.network.buffer.ManagedBuffer
import org.apache.spark.storage.{BlockId, StorageLevel}

import scala.reflect.ClassTag

private[spark]
trait BlockDataManager {

    /**
      * 得到本地数据块的接口。
      *
      * Interface to get local block data. Throws an exception if the block cannot be found or
      * cannot be read successfully.
      */
    def getBlockData(blockId: BlockId): ManagedBuffer

    /**
      * 根据给定的存储模式,把数据存储到本地。
      *
      * Put the block locally, using the given storage level.
      *
      * Returns true if the block was stored and false if the put operation failed or the block
      * already existed.
      */
    def putBlockData(
                            blockId: BlockId,
                            data: ManagedBuffer,
                            level: StorageLevel,
                            classTag: ClassTag[_]): Boolean

    /**
      * 释放[[putBlockData（）]]和[[getBlockData（）]]获取的锁。
      *
      * Release locks acquired by [[putBlockData()]] and [[getBlockData()]].
      */
    def releaseLock(blockId: BlockId, taskAttemptId: Option[Long]): Unit
}
