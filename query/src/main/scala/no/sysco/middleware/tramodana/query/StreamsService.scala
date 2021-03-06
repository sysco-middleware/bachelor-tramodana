package no.sysco.middleware.tramodana.query

import java.util.Properties

import no.sysco.middleware.tramodana.query.AppConfig.QueryConfig
import no.sysco.middleware.tramodana.schema.Topic
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.state.{KeyValueIterator, KeyValueStore, QueryableStoreTypes, ReadOnlyKeyValueStore}
import org.apache.kafka.streams.{KafkaStreams, StreamsBuilder, StreamsConfig, Topology}

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}


class StreamsService(config: QueryConfig)(implicit executionContext: ExecutionContext) {

  val storageName: String = config.kafka.storageName
  val props: Properties = getProps(
    config.name,
    config.kafka.bootstrapServers,
    s"${config.http.host}:${config.http.port}")

  val builder = new StreamsBuilder
  val topology = buildTopology(builder)
  val streams: KafkaStreams = new KafkaStreams(topology, props)

  def getProps(appName: String, kafkaBootstrapServer: String, queryHostPort: String): Properties = {
    val props = new Properties
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServer)
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, appName)
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServer)
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String.getClass)
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String.getClass)
    props.put(StreamsConfig.APPLICATION_SERVER_CONFIG, queryHostPort)
    props
  }

  def buildTopology(builder: StreamsBuilder): Topology = {
    val oNameStorageXml =
      builder.table(
        Topic.ROOT_OPERATION_BPMN_XML,
        Materialized.as[String, String, KeyValueStore[Bytes, Array[Byte]]](storageName))
    builder.build()
  }

  def getValueByKey(key: String): Future[Option[BpmnFlow]] = {
    val storeType = QueryableStoreTypes.keyValueStore[String, String]()
    val keyValueStore: ReadOnlyKeyValueStore[String, String] = streams.store(storageName, storeType)
    val value = keyValueStore.get(key)
    println(value)

    if (value == null) {
      Future(Option.empty)
    } else {
      Future(Option(BpmnFlow(key, value)))
    }

  }

  def getAllValues(): Future[List[BpmnFlow]] = {
    val storeType = QueryableStoreTypes.keyValueStore[String, String]()
    val keyValueStore: ReadOnlyKeyValueStore[String, String] = streams.store(storageName, storeType)

    val it: KeyValueIterator[String, String] = keyValueStore.all()
    var list = new ListBuffer[BpmnFlow]()

    while (it.hasNext) {
      val nextKV = it.next
      list += BpmnFlow(nextKV.key, nextKV.value)
    }

    Future(list.toList)
  }
}
