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

package org.apache.spark.scheduler

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}

import org.apache.spark.internal.Logging
import org.apache.spark.scheduler.SchedulingMode.SchedulingMode

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/**
  * 表示Pools池或任务集管理器集合的可调度实体。
  *
  * A Schedulable entity that represents collection of Pools or TaskSetManagers
  */
private[spark] class Pool(
                                 val poolName: String,
                                 val schedulingMode: SchedulingMode,
                                 initMinShare: Int,
                                 initWeight: Int)
        extends Schedulable with Logging {

    val schedulableQueue = new ConcurrentLinkedQueue[Schedulable]
    val schedulableNameToSchedulable = new ConcurrentHashMap[String, Schedulable]
    val weight = initWeight
    val minShare = initMinShare
    val priority = 0
    val name = poolName
    private val taskSetSchedulingAlgorithm: SchedulingAlgorithm = {
        schedulingMode match {
            case SchedulingMode.FAIR =>
                new FairSchedulingAlgorithm()
            case SchedulingMode.FIFO =>
                new FIFOSchedulingAlgorithm()
            case _ =>
                val msg = s"Unsupported scheduling mode: $schedulingMode. Use FAIR or FIFO instead."
                throw new IllegalArgumentException(msg)
        }
    }
    var runningTasks = 0
    // A pool's stage id is used to break the tie in scheduling.
    var stageId = -1
    var parent: Pool = null

    override def addSchedulable(schedulable: Schedulable) {
        require(schedulable != null)
        schedulableQueue.add(schedulable)
        schedulableNameToSchedulable.put(schedulable.name, schedulable)
        schedulable.parent = this
    }

    override def removeSchedulable(schedulable: Schedulable) {
        schedulableQueue.remove(schedulable)
        schedulableNameToSchedulable.remove(schedulable.name)
    }

    override def getSchedulableByName(schedulableName: String): Schedulable = {
        if (schedulableNameToSchedulable.containsKey(schedulableName)) {
            return schedulableNameToSchedulable.get(schedulableName)
        }
        for (schedulable <- schedulableQueue.asScala) {
            val sched = schedulable.getSchedulableByName(schedulableName)
            if (sched != null) {
                return sched
            }
        }
        null
    }

    override def executorLost(executorId: String, host: String, reason: ExecutorLossReason) {
        schedulableQueue.asScala.foreach(_.executorLost(executorId, host, reason))
    }

    override def checkSpeculatableTasks(minTimeToSpeculation: Int): Boolean = {
        var shouldRevive = false
        for (schedulable <- schedulableQueue.asScala) {
            shouldRevive |= schedulable.checkSpeculatableTasks(minTimeToSpeculation)
        }
        shouldRevive
    }

    override def getSortedTaskSetQueue: ArrayBuffer[TaskSetManager] = {
        var sortedTaskSetQueue = new ArrayBuffer[TaskSetManager]
        val sortedSchedulableQueue =
            schedulableQueue.asScala.toSeq.sortWith(taskSetSchedulingAlgorithm.comparator)
        for (schedulable <- sortedSchedulableQueue) {
            sortedTaskSetQueue ++= schedulable.getSortedTaskSetQueue
        }
        sortedTaskSetQueue
    }

    def increaseRunningTasks(taskNum: Int) {
        runningTasks += taskNum
        if (parent != null) {
            parent.increaseRunningTasks(taskNum)
        }
    }

    def decreaseRunningTasks(taskNum: Int) {
        runningTasks -= taskNum
        if (parent != null) {
            parent.decreaseRunningTasks(taskNum)
        }
    }
}
