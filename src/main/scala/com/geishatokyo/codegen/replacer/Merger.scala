package com.geishatokyo.codegen.replacer

import parser._
import parser.HoldBlock
import parser.ReplaceBlock
import scala.Some

/**
 * 
 * User: takeshita
 * DateTime: 13/09/09 16:04
 */
class Merger {
  val parser = new ReplaceMarkerParser

  val lineSep = "\n"


  def merge( base : String, replace : String) = {

    val baseBlocks = parser.parse(base)
    val replaceBlocks = parser.parse(replace)

    val topLevel = getTopLevel(baseBlocks)
    if(topLevel != getTopLevel(replaceBlocks)){
      throw new Exception("Top level block types are different.")
    }

    val s = topLevel match{
      case TopLevel.Hold => {
        holdMerge(baseBlocks,replaceBlocks)
      }
      case TopLevel.Replace => {
        replaceMerge(baseBlocks,replaceBlocks)
      }
      case _ => {
        toString(replaceBlocks)
      }
    }

    if (s.length > 0){
      s.substring(0,s.length - lineSep.length)
    }else{
      s
    }

  }



  protected def holdMerge( base : List[Block], replace : List[Block]) : String = {
    val builder = new StringBuilder()
    val nameMap = toNameMap(base)

    replace.foreach({
      case StringBlock(lines) => {
        lines.foreach(l => builder.append(l + lineSep))
      }
      case HoldBlock(name,blocks) => {
        nameMap.get("hold." + name) match{
          case Some(_blocks) => {
            builder.append(replaceMerge(_blocks,blocks))
          }
          case _ => {
            println("HoldBlock:" + name + " not found.")
            builder.append(toString(blocks))
          }
        }
      }
      case ReplaceBlock(name,blocks) => {
        builder.append(toString(blocks))
      }
    })
    builder.toString()
  }

  protected def replaceMerge(base : List[Block],replace : List[Block]) : String = {
    val builder = new StringBuilder()
    val nameMap = toNameMap(replace)

    base.foreach({
      case StringBlock(lines) => {
        lines.foreach(l => builder.append(l + lineSep))
      }
      case HoldBlock(name,blocks) => {
        builder.append(toString(blocks))

      }
      case ReplaceBlock(name,blocks) => {
        nameMap.get("replace." + name) match{
          case Some(_blocks) => {
            builder.append(holdMerge(blocks,_blocks))
          }
          case _ => {
            println("ReplaceBlock:" + name + " not found.")
            builder.append(toString(blocks))
          }
        }
      }
    })
    builder.toString()
  }


  protected def toString( blocks : List[Block]) : String = {
    val builder = new StringBuilder()
    blocks.foreach({
      case StringBlock(lines) => {
        lines.foreach(l => builder.append(l + lineSep))
      }
      case HoldBlock(_,blocks) => {
        builder.append(toString(blocks))
      }
      case ReplaceBlock(_,blocks) => {
        builder.append(toString(blocks))

      }
    })
    builder.toString()
  }


  protected def toNameMap(blocks : List[Block]) = {
    blocks.collect({
      case HoldBlock(name,blocks) => ("hold." + name) -> blocks
      case ReplaceBlock(name,blocks) => ("replace." + name) -> blocks
    }).toMap

  }

  protected def getTopLevel(blocks : List[Block]) = {

    val topLevel = blocks.collectFirst({
      case HoldBlock(_,_) => TopLevel.Hold
      case ReplaceBlock(_,_) => TopLevel.Replace
    })

    topLevel match{
      case Some(TopLevel.Hold) => {
        if (blocks.exists(_.isInstanceOf[ReplaceBlock])){
          throw new Exception("Can't use both hold and replace marker on top level.")
        }
      }
      case Some(TopLevel.Replace) => {
        if (blocks.exists(_.isInstanceOf[HoldBlock])){
          throw new Exception("Can't use both hold and replace marker on top level.")
        }
      }
      case _ =>
    }

    topLevel getOrElse TopLevel.Hold

  }



}

object TopLevel extends Enumeration{
  val Nothing,Hold,Replace = Value
}
