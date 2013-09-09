package com.geishatokyo.codegen

import dsl.ModelConverter
import dsl.parser.GenericDSLParser
import exporter.FileExporter
import generator.{Context, CodeGenerator}
import util.FileUtil

/**
 * 
 * User: takeshita
 * DateTime: 13/09/09 17:32
 */
class Generator {

  private val parser = new GenericDSLParser {}

  private var modelConverters : Map[Manifest[_],ModelConverter[_]] = Map.empty
  private var codeGenerators : Map[Manifest[_],List[CodeGenerator[_]]] = Map.empty
  private var fileExporters : Map[String,List[FileExporter]] = Map.empty

  def +=[T](modelConverter : ModelConverter[T])(implicit m : Manifest[T]) = {
    modelConverters += (m -> modelConverter)
  }

  def +=[T](codeGenerator : CodeGenerator[T])(implicit m : Manifest[T]) = {
    codeGenerators.get(m) match{
      case Some(generators) => {
        codeGenerators += (m -> (codeGenerator :: generators))
      }
      case None => {
        codeGenerators += (m -> List(codeGenerator))
      }
    }
  }

  def +=(fileExporter : FileExporter) = {
    val group = fileExporter.groupName
    fileExporters.get(group) match{
      case Some(exporters) => {
        fileExporters += (group -> (fileExporter :: exporters))
      }
      case None => {
        fileExporters += (group -> List(fileExporter))
      }
    }
  }


  def generate( dslFilename : String, dryRun : Boolean = false) = {

    val dsl = FileUtil.read(dslFilename)

    val definitions = parser.parse(dsl)
    implicit val context = Context(definitions)

    val generatedCodes = codeGenerators.flatMap({
      case (m , generators) => {
        modelConverters.get(m) match{
          case Some(converter) => {
            val models = converter.convert(definitions)
            generators.flatMap(g => g.asInstanceOf[CodeGenerator[Any]].generate(models))
          }
          case None => {
            Nil
          }
        }
      }
    })

    val files = generatedCodes.groupBy(_.group).map({
      case (groupName,codes) => {
        val tempFiles =  fileExporters.get(groupName) match{
          case Some(exporters) => {
            exporters.flatMap(fe => {
              fe.beforeExportToTemp()
              val t = codes.map(c => c -> fe.exportToTemp(c))
              fe.afterExport()
              t
            })
          }
          case None => {
            println("No file exporter for group:" + groupName)
            Nil
          }
        }
        groupName -> tempFiles
      }
    })


    if (!dryRun){
      files.foreach({
        case (group,codes) => {
          val exporters = fileExporters(group)
          exporters.foreach(e => {
            e.beforeExport()
            codes.foreach( c => {
              e.export(c._1,c._2)
            })
            e.afterExport()
          })

        }
      })

    }else{
      println("Dry run.So files aren't exported to actual folder.")
    }


  }


}