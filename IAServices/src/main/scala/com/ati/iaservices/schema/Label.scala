package com.ati.iaservices.schema

import scala.reflect.BeanProperty
import com.protegra_ati.agentservices.core.schema.Data
import com.protegra_ati.agentservices.store.extensions.StringExtensions._

class LabelKey (@BeanProperty var searchKey : String) extends Data {
  def this() = this("_")

  override protected def rawFieldsForSearchAndStoreKey(): List[ String ] = List("searchKey") ::: super.rawFieldsForSearchAndStoreKey

  override def toString(): String =
  {
    {
      "LabelKey[id=" + id + ", locale=" + localeCode + ", searchKey=" + searchKey + "]"
    }
  }
}

object LabelKey
{
  final val SEARCH_ALL_KEY = new LabelKey().toSearchKey

  final val SEARCH_ALL = new LabelKey()
  {
    override def toSearchKey(): String = LabelKey.SEARCH_ALL_KEY
  }
}

case class Label[ T <: ContentValue ] (
  @BeanProperty var key: LabelKey,
  @BeanProperty var content: Content[T]
) extends Data {

  //need a no-parameter constructor to create a version of the object with only authorized fields populated
  def this() = this(new LabelKey(), null.asInstanceOf[Content[T]])

  var contentType = formattedContentValueName

  def formattedContentValueName: String =
  {
    if (content == null || content.value == null)
      return ""

    content.value.getClass.getName.trimPackage
  }
  override protected def ignoredFieldsForSearchAndStoreKey(): List[ String ] = List("content") ::: super.ignoredFieldsForSearchAndStoreKey

  override def toString(): String =
  {
    {
      "Label[id=" + id + ", locale=" + localeCode + ", key=" + key + ", content=" + content + "]"
    }
  }
}

object Label
{
  final val SEARCH_ALL_KEY = new Label().toSearchKey

  final val SEARCH_ALL = new Label()
  {
    override def toSearchKey(): String = Label.SEARCH_ALL_KEY
  }

}
