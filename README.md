> Universidad Nacional de Córdoba \ Facultad de Matemática, Astronomía,
Física y Computación \ Paradigmas de la Programación 2020

# LABORATORIO 3 - Programación Concurrente Usando Actores

Para este proyecto fue fundamental profundizar en la investigación  de la
programación concurrente mediante el uso del modelo de actores, todas sus
características, su uso correcto, y el manejo de *Futures*. Una vez comprendido
todos estos conceptos, debimos elegir qué estructura utilizar para la aplicación
de un RSS Feed Aggregator, y elegir entre Typed Actors y Untyped Actors.

## DECISIONES DE DISEÑO
La primera decisión tomada, fue basarnos en la implementación de actores tipados
en el estilo de programación funcional. Hubo dos razones para tomar esta decisión:
- La primera es que este tipo de actores cuentan con una interfaz estática y solo
  responden a esa interfaz, lo que genera una cierta confiabilidad y seguridad.
- Y la otra es que la guía de [Akka](https://doc.akka.io//docs/akka/current/typed/
  actors.html) es bastante más detallada con este tipo de implementación.
Otra decisión tomada fue la de modularizar el proyecto separando por un lado   
el servidor y las rutas con sus respectivos endpoints, y por otro, el soporte  
json y los actores en sus respectivos archivos.

##  PARTE 1: Feeds à la carte
En un principio tuvimos que buscar la manera de implementar el esqueleto que se
nos había brindado adecuándolo a los actores tipados. Tratamos de lograr una
mayor modularización para darle forma a la REST API. Luego, pasamos a crear el
actor que manejaría las operaciones necesarias para darle una respuesta a los
endpoint pedidos en la consigna. Además, buscamos la forma de lograr una
comunicación asíncrona. Finalmente, se buscó resolver el manejo de errores en
la validación de los parámetros de entrada `url` y `since` en los métodos
`isValidURL`, utilizando la librería de java *java.net.URL*, y `isValidSince`
utilizando las librerías: *java.time.format.DateTimeFormatter*,
*java.time.LocalDateTime* y *java.time.format.ResolverStyle* ya que no nos
terminaba de convencer la librería *SimpleDateFormat* recomendada por la consigna.
En ambos métodos utilizamos la sintaxis Try{...} para centrarnos en un enfoque
mas funcional. Lo más costoso de esta parte fue aprender a trabajar con Futures
para lograr la asincronicidad deseada, pero una vez comprendido el concepto de los
Futures, el progreso se volvió más fluido.

## PARTE 2: Un agregador de feeds
Para conseguir que nuestra aplicación pudiera almacenar en memoria los feeds
suscritos, optamos por guardar las urls de dichos feeds en una lista dentro del
actor RSSManager y posteriormente para el endpoint feeds, mapearlas para extraer
los elementos xml y consecuentemente los FeedInfo requeridos. Para esto, nos valimos
del método *sequence*, un manejo más cómodo de la lista de Futures.  

## PARTE 3: Múltiples usuarios  
En esta parte, debimos: crear un nuevo endpoint en el que se pudiera ir añadiendo  
usuarios, para ello crear nuevos mensajes llamados AddUser y AddUserResponse en Manager,  
sustituir lista de urls por una colección de pares mutables, para que además de guardar  
usuarios, se guarden las suscripciones de url de cada uno de estos, modificar el   
comportamiento cuando ingresan los mensajes Subscribe y Feeds en Manager y agregar el  
nuevo comportamiento del mensaje recibido AddUser.

##PARTE 4: Múltiples feed parsers
Finalmente, modificamos el método fromXml y lo extendimos, para que extraiga  
feeds RSS y feeds Atom. Ambos son formato XML y solo tiene algunas diferencias al  
momento de parsear, por lo que tuvimos que crear FeedEntry, FeedInfoAtom en JsonFormats.  
Además, para que el parsing no sea bloqueante y asíncrono, lo envolvimos en un Future.  

## PREGUNTAS Y RESPUESTAS

**¿Cómo hicieron para comunicarse con el "mundo exterior"?**
Cuando una request llega al servidor, es atendida por el servidor y enrutada (en   
RSSRoutes.scala) según los parámetros de entrada. Luego, el servidor realiza la   
validación de datos de entrada correspondientes, y si los datos son validados,  
se procede a realizar la petición correspondiente al actor `RSSManager` (o Manager,  
ya para la parte 3 y 4), el cual nos devuelve la respuesta de manera asíncrona.  
La comunicación que se realiza entre RSSManager y el servidor, es mediante mensajes.  
El actor RSSManager recibe mensajes de "alguien", es decir, se abstrae de
quién es ese "alguien".  

**¿Qué son los Futures?, ¿Para qué fueron utilizados en su implementación?**
Los Futures son contenedores para un valor que aún puede no existir, proporcionan
una abstracción de la concurrencia que el/la programador/a puede aprovechar para
centrarse en las partes más importantes del problema. En nuestro caso, en particular,  
usamos los Futures para obtener la asincronicidad requerida para la funcionalidad
solicitada por la consigna, más concretamente, debimos manejar los Futures devueltos
por el método ask (o `?`) y también al momento de extraer los elementos xml de las
url solicitadas.

**¿Qué problemas traería el implementar este sistema de manera síncrona?**
El problema se vería cuando al server le llegaran muchas peticiones de múltiples
usuarios de manera simultanea, ésto podría generar un cuello de botella, disminuyendo
el rendimiento del sistema.


**¿Qué les asegura el sistema de pasaje de mensajes y cómo se diferencia con un semáforo/mutex?**
El sistema de pasaje de mensajes asegura la concurrencia implícita, pues cada actor
responde a los mensajes en el orden en el que le llegan mediante un canal de
comunicación asíncrono, evitando así los efectos secundarios no deseados. La
diferencia con los semáforos y los mutex, es que en el sistema de pasaje de mensajes
no me preocupo de las cuestiones de bajo nivel ya que la concurrencia obtenida
surge de manera natural.

## DIAGRAMA  
akka://FeedAggregatorServer/user
               |
akka://FeedAggregatorServer/user/RSSManager

- JERARQUÍA DE ACTORES 
Modelamos un actor unificado `RSSManager` porque tenemos una variable que necesita   
ser compartida para varios endpoints, que es urls en un comienzo. Para los puntos  
estrellas, En cuanto a la jerarquía de actores, quisimos seguir manteniendo la misma.  

	- Parte 3 (Punto estrella)
		Sustituimos lista de urls por una colección de pares mutables, donde contiene   
		a los usuarios y las subscripciones a las urls. Agregamos nuevo mensaje que podría  
		recibir Manager y su comportamiento (AddUser), y el mensaje de respuesta que daría.
	- Parte 4 (Punto estrella)
		Para que nuestro Feeds Aggregator agregue feeds Atom, buscamos modificar nuestro  
		método fromXml, no sólo para que contemple estos feeds, si no también para que lo haga  
		de manera asíncrona y no bloqueante con Future.   

## Protocolo de mensajes entre actores
El actor RSSManager recibe los siguientes mensajes: Feed, Suscribe, Feeds. Mensajes que le  
son enviados por el servidor. Responde con los mensajes:   
FeedResponse, SuscribeResponse, FeedsResponse respectivamente.  
- Para la parte 3, Manager (anteriormente RSSManager) recibe el mensaje AddUser  
y éste responde con el mensaje AddUserResponse.  
- Para la parte 4, el protocolo de mensajes no es alterado, ya que solo modificamos  
un método interno del actor Manager.