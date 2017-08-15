package com.kent.workflow.actionnode

import com.kent.workflow.node.ActionNodeInstance
import com.kent.workflow.node.NodeInstance
import scala.sys.process.ProcessLogger
import com.kent.main.Worker
import com.kent.pub.Event._
import com.kent.coordinate.ParamHandler
import scala.sys.process._
import java.util.Date
import java.io.PrintWriter
import java.io.File
import com.kent.util.Util
import com.kent.db.LogRecorder.LogType
import com.kent.db.LogRecorder.LogType._
import com.kent.db.LogRecorder

class FileExecutorNodeInstance(nodeInfo: FileExecutorNode) extends ActionNodeInstance(nodeInfo)  {
  private var executeResult: Process = _

  override def execute(): Boolean = {
    try {
      var location = Worker.config.getString("workflow.action.script-location") + "/" + s"action_${this.id}_${this.nodeInfo.name}"
      this.actionActor.workflowActorRef
      
      
      
      var newLocation = if(nodeInfo.location == null || nodeInfo.location == "")
            Worker.config.getString("workflow.action.script-location") + "/" + Util.produce8UUID
            else nodeInfo.location
      //把文件内容写入文件
      val writer = new PrintWriter(new File(newLocation))
      //删除前置空格
      val lines = nodeInfo.content.split("\n").map { x => 
        if(x.trim() != "")x.trim() else null
      }.filter { _ != null }
      val trimContent = lines.mkString("\n")
      writer.write(trimContent)
      writer.flush()
      writer.close()
      LogRecorder.info(ACTION_NODE_INSTANCE, this.id, this.nodeInfo.name, s"代码写入到文件：${newLocation}")
      //执行
      var cmd = "sh";
      if(lines.size > 0){  //选择用哪个执行解析器
        if(lines(0).toLowerCase().contains("perl")) cmd = "perl"
        else if(lines(0).toLowerCase().contains("python")) cmd = "python"
      }
      
      val pLogger = ProcessLogger(line => LogRecorder.info(ACTION_NODE_INSTANCE, this.id, this.nodeInfo.name, line),
                                  line => LogRecorder.error(ACTION_NODE_INSTANCE, this.id, this.nodeInfo.name, line))
      executeResult = Process(s"${cmd} ${newLocation}").run(pLogger)
      
      if(executeResult.exitValue() == 0) true else false
    }catch{
      case e:Exception => 
        e.printStackTrace();
        LogRecorder.error(ACTION_NODE_INSTANCE, this.id, this.nodeInfo.name, e.getMessage)
        false
    }
  }

  def replaceParam(param: Map[String, String]): Boolean = {
    nodeInfo.location = ParamHandler(new Date()).getValue(nodeInfo.location, param)
    nodeInfo.content = ParamHandler(new Date()).getValue(nodeInfo.content, param)
    true
  }

  def kill(): Boolean = {
    if(executeResult != null){
      executeResult.destroy()
    }
    true
  }
}

object FileExecutorNodeInstance {
  def apply(san: ScriptNode): FileExecutorNodeInstance = new FileExecutorNodeInstance(san)
}