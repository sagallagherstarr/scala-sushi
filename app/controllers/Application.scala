package controllers

import play.api.Logger

import play.api._
import play.api.mvc._

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import scala.concurrent.Future
import play.api.Play.current
import play.api.libs.ws._
import play.api.libs.ws.ning.NingAsyncHttpClientConfigBuilder
// Reactive Mongo imports
import reactivemongo.api._

// Reactive Mongo plugin, including the JSON-specialized collection
import play.modules.reactivemongo.MongoController
import play.modules.reactivemongo.json.collection.JSONCollection

object Application extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is again ready."))
  }

}


/*
 * Example using ReactiveMongo + Play JSON library.
 *
 * There are two approaches demonstrated in this controller:
 * - using JsObjects directly
 * - using case classes that can be turned into Json using Reads and Writes.
 *
 * This controller uses case classes and their associated Reads/Writes
 * to read or write JSON structures.
 *
 * Instead of using the default Collection implementation (which interacts with
 * BSON structures + BSONReader/BSONWriter), we use a specialized
 * implementation that works with JsObject + Reads/Writes.
 *
 * Of course, you can still use the default Collection implementation
 * (BSONCollection.) See ReactiveMongo examples to learn how to use it.
 */
object ApplicationUsingJsonReadersWriters extends Controller with MongoController {
  /*
   * Get a JSONCollection (a Collection implementation that is designed to work
   * with JsObject, Reads and Writes.)
   * Note that the `collection` is not a `val`, but a `def`. We do _not_ store
   * the collection reference to avoid potential problems in development with
   * Play hot-reloading.
   */
  def collection: JSONCollection = db.collection[JSONCollection]("persons")

  // ------------------------------------------ //
  // Using case classes + Json Writes and Reads //
  // ------------------------------------------ //
  import play.api.data.Form
  import models._
  import models.JsonFormats._

  def create = Action.async {
    val user = User(29, "John", "Smith", List(
      Feed("Slashdot news", "http://slashdot.org/slashdot.rdf")))
    // insert the user
    val futureResult = collection.insert(user)
    // when the insert is performed, send a OK 200 result
    futureResult.map(_ => Ok)
  }

  def createFromJson = Action.async(parse.json) { request =>
    /*
     * request.body is a JsValue.
     * There is an implicit Writes that turns this JsValue as a JsObject,
     * so you can call insert() with this JsValue.
     * (insert() takes a JsObject as parameter, or anything that can be
     * turned into a JsObject using a Writes.)
     */
    request.body.validate[User].map { user =>
      // `user` is an instance of the case class `models.User`
      collection.insert(user).map { lastError =>
        Logger.debug(s"Successfully inserted with LastError: $lastError")
        Created
      }
    }.getOrElse(Future.successful(BadRequest("invalid json")))
  }

  def findByName(lastName: String) = Action.async {
    // let's do our query
    val cursor: Cursor[User] = collection.
      // find all people with name `name`
      find(Json.obj("lastName" -> lastName)).
      // sort them by creation date
      sort(Json.obj("created" -> -1)).
      // perform the query and get a cursor of JsObject
      cursor[User]

    // gather all the JsObjects in a list
    val futureUsersList: Future[List[User]] = cursor.collect[List]()

    // everything's ok! Let's reply with the array
    futureUsersList.map { persons =>
      Ok(persons.toString)
    }
  }
}

/*
    "VendorName": "EBSCOhost",
    "Description": "EBSCOhost SUSHI endpoint",
    "RequestorID": "8746ed38-5d72-44c9-ba06-b0d1d15b3238",
    "RequestorName": "Scott Gallagher-Starr",
    "RequestorEmail": "sgallagherstarr@nwcu.edu",
    "CustomerID": "s8887269",
    "CustomerName": "NCU",
    "CounterRelease": "4",
    "wsdlURL": "http://sushi.ebscohost.com/R4/SushiService.svc",
    "ReportsAvailable": ["JR1", "JR5", "DR1", "DR2", "PR1", "BR1", "BR2", "BR3" ],
    "VendorRequiresFields": ["RequestorID", "CustomerID", "wsdlURL"]


*/
object WSDLController extends Controller {
    val logger = Logger(this.getClass())
    
    def datatest = Action.async {
        logger.error(s"datatest entered.")
        
        val ebsco_request = 
          <ReportRequest>
            <Requestor>
              <ID>8746ed38-5d72-44c9-ba06-b0d1d15b3238</ID>
              <Name>NorthWest Christian University</Name>
              <Email>sgallagherstarr@nwcu.edu</Email>
            </Requestor>
            <CustomerReference>
              <ID>s8887269</ID>
              <Email>sgallagherstarr@nwcu.edu</Email>
            </CustomerReference>
            <ReportDefinition Name="JR1" Release="4">
              <Filters>
                <UsageDateRange>
                  <Begin>2014-11-01</Begin>
                  <End>2014-11-30</End>
                </UsageDateRange>
              </Filters>
            </ReportDefinition>
          </ReportRequest>
        
        
        logger.error(s"ebsco_request is $ebsco_request")
        logger.error(s"ebsco_request class is ${ebsco_request.getClass()}")
        val EBSCOUrl = "http://sushi.ebscohost.com/R4/SushiService.svc?wsdl"
        val holder: WSRequestHolder = WS.url(EBSCOUrl)
        
        val wsdlDefFuture = holder.get()
        
        wsdlDefFuture.map {
            response => { logger.error(
                s"""result status is ${response.status} (${response.statusText})\n
                  result.headers is ${response.allHeaders.toString()}\n
                  result cookies ${response.cookies}\n"""); 
                Ok(response.xml) }// response.xml \ "message"
        }
/*        
        val complexHolder: WSRequestHolder =
            holder.withHeaders("Content-Type" -> "text/xml",
                               "Charset" -> "utf-8" )
        
        val msg = views.xml.soapenvelope(ebsco_request)
        
        logger.error(s"msg is $msg")
        logger.error(s"msg class is ${msg.getClass()}")
        val futureResult = complexHolder.post(msg)
        
        futureResult.map {
            response => { logger.error(
                s"""result status is ${response.status} (${response.statusText})\n
                  result.headers is ${response.allHeaders.toString()}\n
                  result cookies ${response.cookies}\n"""); 
                Ok(response.body) }// response.xml \ "message"
        }
*/
    }

/*    
    def msgwrap(xml: scala.xml.Node) : String = {
        val buf = new StringBuilder
        buf.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n")
        buf.append("<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">\n")
        buf.append("<SOAP-ENV:Body>\n")
        buf.append(xml.toString)
        buf.append("\n</SOAP-ENV:Body>\n")
        buf.append("</SOAP-ENV:Envelope>\n")
        buf.toString
    }
*/
}