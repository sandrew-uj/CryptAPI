# CryptAPI
## Использованные библиотеки
Как и оговаривалось в условии задачи использоватл библиотеку для
`HttpClient`, а также для сериализации `json` использовал `com.fasterxml.jackson`

## Реализация пропускной способности
По условию необходимо было, чтобы:


>При превышении лимита запрос вызов должен блокироваться,
>чтобы не превысить максимальное количество запросов к API и
>продолжить выполнение, без выбрасывания исключения, когда
>ограничение на количество вызов API не будет превышено в
>результате этого вызова. В любой ситуации превышать лимит на
>количество запросов запрещено для метода.


Для этого использовал стандартный класс `Semaphore` из стандартной
библиотеки `java.concurrency`. Это такой класс который позволяет
выполнять только максимум изначально заданное число операций параллельно


Идея решения заключается в том, чтобы при каждом вызове нашего метода
`createDocument()` создавать поток, который по истечении заданного
`TimeUnit` освободит `semaphore`, таким образом, у нас никогда не будет
такой ситуации, что на протяжении прошедшего `timeUnit` было
вызвано больше чем задано операций (это будет обеспечиваться с помощью семафора)


Если же количество потоков будет превышать значение в `10000`, то
не будет отправляться новых запросов до того момента пока количество
потоков не уменьшится и будет возвращаться код `429 Too many requests`


## Тестирование
Обратите внимание, что все классы протестированы с помощью `JUnit5`
и тесты раполагаются в модуле `test`

## Документация
Также ко всему коду написана javadoc документация