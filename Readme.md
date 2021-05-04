# Spring Boot empowered Kubernetes & Exploring Tekton

Dieses Repository enthält alle Sourcen zum Talk von Legital Learning (www.legital.org). Alle Pfade beziehen sich auf das 
Root-Verzeichnis dieses Projekts. Am besten funktioniert es, wenn ihr euch dieses Repository forked. Für das Bauen von 
Docker Images ist eine [Installation von Docker](https://docs.docker.com/get-docker/) notwendig.

### Minikube installieren und lokale Docker Registry einrichten

1. Wir benötigen ein K8s-Cluster. Wenn ihr gerade keins zur Hand habt, ladet euch Minikube und installiert es euch.
   Wie das geht, seht ihr hier: https://kubernetes.io/de/docs/tasks/tools/install-minikube/
   <br><br>
   Um auf einen Blick sehen zu können, was in dem Cluster passiert, könnt ihr bei laufendem Minikube noch folgenden
   Befehl ausführen. Solange dieser läuft, habt ihr Zugriff auf das K8s-Dashboard.
   ````shell
   minikube dashboard
   ````
   <br>
2. Als Nächstes muss das registry addon aktiviert werden.
   ```shell
   minikube addons enable registry
   ```
   Wenn alles geklappt habt, dann seht ihr im Namespace `kube-system` einen laufenden Pod mit dem Prefix `registry-`und
   einen mit dem Prefix `registry-proxy-`. Außerdem sollte noch ein Service `registry` dazugekommen sein.
   <br>
   <br>
3. Danach läuft zwar die Registry, doch wir müssen noch sicherstellen, dass wir immer auf dieselbe URL zugreifen können
   um zu pushen und zu pullen. Das funktioniert bisher noch nicht, da es einen Unterschied macht, ob man von innerhalb
   eines Pods kommt oder das K8s-Cluster selber zugreift. Dafür etablieren wir jetzt den Registry-Alias `registry.local`.
   ```shell
   kubectl apply -f k8s/configure-registry.yaml
   ```

## HowTo: Spring Boot empowered Kubernetes

Mit Spring Boot 2.3 erhielt die Integration von Kubernetes (K8s) Einzug in das Framework. Wir können auf einfache Weise 
ein Docker Image via Maven erstellen. Da JDK 11 Support für TLS 1.3 hat, ist betriebssystemabhängig das JVM Argument 
-Djdk.tls.client.protocols=TLSv1.2 erforderlich, wenn wir später das Image im Kontext von Minikube erzeugen.

```shell
mvn spring-boot:build-image -Dspring-boot.build-image.imageName=registry.local/legital/k8s-probes-demo -Djdk.tls.client.protocols=TLSv1.2
```

Das erzeuge Docker Image und damit unsere Spring Boot Anwendung können wir ausführen. Mit dem Parameter -e können wir Environment
Variables setzen, -p nutzen wir um den Port für uns zugänglich zu machen und -t definiert, welches Docker Image wird starten wollen.

```shell
docker run -e "SPRING_PROFILES_ACTIVE=dev" -p 8080:8080 -t registry.local/legital/k8s-probes-demo
```

Die Seite http://localhost:8080/actuator/health liefert uns eine Antwort. Wohingegen die Seite
http://localhost:8080/actuator/liveness mit einer Error Page antwortet. Wir stellen also fest, dass Spring keine 
Autoconfiguration für liveness und readiness Probes erstellt hat. Wir können den Docker Container beenden:

```shell
docker ps
docker stop <CONTAINER_ID>
```

Für das Deployment auf K8s nutzen wir folgend Minikube.
Damit wir unsere Befehle auf dem Docker Environment von Minikube ausführen, nutzen wir folgenden Befehl.

```shell
eval $(minikube docker-env --shell bash)
```

Das gebaute Docker Image kann in die lokale Registry von Minikube gepusht werden.

```shell
docker push registry.local/legital/k8s-probes-demo:latest
```

Nach erfolgreichem Bauen des Docker Images können wir prüfen, ob das Docker Image in unserer lokalen Docker Repository vorliegt

```shell
docker images
```

Wir deployen unsere Applikation auf K8s.

```shell
export IMAGE=registry.local/legital/k8s-probes-demo:latest
envsubst < k8s/application/deployment.yaml | kubectl apply -f -
```

Im K8s Dashboard sollte der Pod grün dargestellt werden. Um unseren Pod aufrufen zu können, erstellen wir noch einen K8s Service.

```shell
kubectl expose deployment k8s-probes-demo --type=NodePort --name=k8s-probes-demo-service
minikube service k8s-probes-demo-service
```

Die Seite http://<public-ip>:<public-port>/actuator/health sowie die Seite http://<public-ip>:<public-port>/actuator/health/liveness 
liefern uns jeweils eine Antwort.

## HowTo: Tekton Build on minikube

Nachdem das Kotlin Projekt lokal zum Laufen gebracht wurde, kommt jetzt Tekton mit K8s ins Spiel. Zielbild ist, dass
wir eine Build-Pipeline bauen, die folgende Schritte enthält:
- Checkout einer bestimmten Revision aus einem frei zu spezifizierenden Git-Repository
- Maven build
- Docker build mit der erstellten .jar-Datei
- (Re-)Deployment des Docker-Images auf dem K8s-Cluster

Optional lässt sich die Pipeline so erweitern, dass sie per REST-Call gestartet werden kann, sodass der Build
automatisiert von einem WebHook (z.B. aus github heraus) ausgelöst werden kann.

### Tekton installieren und alles vorbereiten

1. Als erstes installieren wir in unserem K8s-Cluster Tekton. Genauere Informationen dazu findet ihr auch hier: 
   https://tekton.dev/docs/getting-started/ TLDR; Das geht ganz einfach mit folgendem Befehl:
   ```shell
    kubectl apply --filename https://storage.googleapis.com/tekton-releases/pipeline/latest/release.yaml
   ```
   Tekton benötigt eigenen Speicher zum Funktionieren. In einem bereits eingerichtetene K8s-Cluster müsste alles 
   Out-of-the-Box funktionieren. Wir müssen in unserem K8s-Cluster aber noch den Speicher bereitstellen. Darum gehen wir
   in die VM von minikube und erstellen einen neuen Ordner. Dann erstellen wir im Cluster ein neues PersistentVolume,
   welches den neuen Ordner im Cluster verfügbar macht.
   ```shell
    minikube ssh 
    sudo mkdir -p /data/volumes/tekton
    exit

    kubectl apply -f k8s/tekton/tekton-storage.yaml
   ```
   Außerdem empfehlen wir, dass ihr euch das Tekton-CLI ladet. Es wird zwar nicht zwangsläufig benötigt, erleichtert euch 
   aber den Überblick und die Verwaltung von Tekton-Ressourcen. Wie die Installation geht, seht ihr hier: https://tekton.dev/docs/cli/
   <br>
   <br>
2. _(Optional)_ Um alles an einem Platz zu halten, würden wir empfehlen, dass ihr euch einen eigenen Namespace anlegt. 
   In unserem Beispiel haben wir ihn `playground` genannt.
   ```shell
   kubectl create namespace playground
   ```
   **Hinweis:** Wir werden ab jetzt alles in diesem Namespace installieren. Wenn ihr euren Namespace anders benannt habt,
   müsst ihr die folgenden Befehle entsprechend anpassen.
   <br>
   <br>
3. Wiederverwendbare Arbeitsschritte heißen in Tekton Tasks. Diese legen wir mit folgendem Befehl an.
   ```shell
   kubectl apply -f k8s/tekton/tasks.yaml -n playground
   ```
   <br>
4. Nun legen wir unsere Pipeline an.
   ```shell
   kubectl apply -f k8s/tekton/pipeline.yaml -n playground
   ```
5. Damit wir unseren Code auschecken und am Ende auch deployen zu können, benötigen wir für die Ausführung der Pipeline
   einen Service-Account (wir nennen ihn `build-bot`) erstellen, der alle notwendigen Berechtigungen erhält. 
   <br><br>
   Unser Beispiel benutzt zum Chekout von GitHub
   einen Deployment-Key ([GitHub-Doku](https://docs.github.com/en/developers/overview/managing-deploy-keys#setup-2)). 
   Den Private-Key müsst ihr in der Datei `k8s/tekton/service-account.yaml` einfügen. Danach folgenden Befehl ausführen:
   ```shell
   kubectl apply -f k8s/tekton/service-account.yaml -n playground
   ```
6. Als Letztes müssen wir noch sicherstellen, dass die ausgecheckten Dateien zwischen den einzelnen Build-Steps übergeben
   werden können. Dies passiert auch hier wieder über ein PersistentVolume und einen zugehörigen PersistentVolumeClaim. 
   Wir erstellen in der minikube VM ein Verzeichnis.
   ```shell
   minikube ssh 
   sudo mkdir -p /data/volumes/build
   exit

   kubectl apply -f k8s/tekton/build-storage.yaml -n playground
   ```

### Pipeline starten

**Hinweis:** Bevor ihr diesen Abschnitt bearbeitet, guckt euch [die Datei](/k8s/tekton/pipeline-run.yaml) 
`/k8s/tekton/pipeline-run.yaml` an und ersetzt die Beispielwerte mit den Werten aus eurem Repository.

Nachdem wir alles vorbereitet haben, bleibt nur noch übrig, die Pipeline auszuführen. Dazu erstellen wir einen neuen 
PipelineRun mit folgendem Befehl.
```shell
kubectl create -f k8s/tekton/pipeline-run.yaml -n playground
```
Danach könnt ihr mit dem Tekton-CLI euch den Status des PipelineRuns ausgeben lassen.
```shell
tkn pr list -n playground
```
Wenn alles geklappt hat, dann solltet ihr eine Ausgabe wie diese in eurer Konsole sehen.
``` 
NAME               STARTED         DURATION   STATUS
probes-run-nqxwn   6 seconds ago   ---        Running
```
Wenn ihr den Namen des PipelineRuns kennt, lässt sich das Log folgendermaßen ausgeben. Ersetzt dazu einfach den Namen aus 
dem Beispiel mit dem, eures eigenen PipelineRuns (Wenn ihr `tkn` richtig eingerichtet habt, dann bekommt ihr auch 
Auto-Vervollständigung für den Namen).
```shell
tkn pr logs probes-run-nqxwn -f -n playground
```

### Optional: Pipeline mit Tekton-Triggers per REST-Call starten
1. Da Tekton-Triggers ein eigenes Projekt ist und bisher nicht "serienmäßig" mit der Tekton-Installation ausgeliefert wird, 
   müssen wir es uns zuerst installieren. Die Dokumentation dazu findet ihr hier: https://github.com/tektoncd/triggers/blob/main/docs/install.md
   TLDR; Auf dem minikube-Cluster benötigen wir keine größeren Vorbereitungen und wir führen einfach den folgenden Befehl aus:
   ```shell
   kubectl apply --filename https://storage.googleapis.com/tekton-releases/triggers/latest/release.yaml
   ```
   Das hat zur Folge, dass zusätzliche Custom-Ressources in den Tekton-Namespace (`tekton-pipelines`) installiert werden
   und weitere Pods starten. Guckt euch das über das Minikube-Dashboard an.
   <br>
   <br>
2. Als nächstes müssen wir unseren Service Account mit mehr Rechten ausstatten, sodass wir ihn auch dazu einsetzen können 
   PipelineRuns zu erstellen. Dazu führen wir folgenden Befehl aus:
   ```shell
   kubectl apply -f k8s/tekton-triggers/update-service-account.yaml -n playground
   ```
   <br>
3. Nun müssen wir noch den Trigger und den zugehörigen EventHandler erstellen.
   ```shell
   kubectl apply -f k8s/tekton-triggers/trigger.yaml -n playground
   ```
   Ob alles geklappt hat, seht ihr wieder im minikube-dashboard. Im Namespace `playground` sollte ein Pod gestartet sein,
   der im Namen `el-probes-build-listener-` hat. Desweiteren sollten wir mit
   ```shell
   minikube service -n playground list
   ```
   eine Ausgabe, ähnlich der Folgenden sehen.
   ```shell
   |------------|--------------------------|--------------------|---------------------------|
   | NAMESPACE  |           NAME           |    TARGET PORT     |            URL            |
   |------------|--------------------------|--------------------|---------------------------|
   | playground | el-probes-build-listener | http-listener/8080 | http://192.168.64.4:32439 |
   |------------|--------------------------|--------------------|---------------------------|
   ```
   Merkt euch die URL! Die benötigen für den letzten Schritt.
   <br>
   <br>
4. Hat bis hierhin alles geklappt, bleibt nur noch übrig, einen REST-Call auf die URL auszuführen. Dazu benutzen wir in 
   unserem Besipiel `curl`. Ihr könnt natürlich aber auch gerne REST-Clients wie Postman oder Insomnia benutzen.
   Mit `curl` sieht das Ganze dann so aus (URL entsprechend ersetzen):
   ```shell
   curl http://192.168.64.4:32439 -d "{ \"revision\":\"main\" }"
   ```
   Hiermit startet die Pipeline und baut den Branch "main". Von eurem Erfolg überzeugen, könnt ihr euch dann wieder
   mit `tkn`, wie [hier](#pipeline-starten) beschrieben. 

## Links und Quellen

Hier findet ihr noch einmal alle Links und Quellen, die wir benutzt haben:

### Spring Boot empowered K8s
- Spring Boot Reference: https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/
- Spring Boot Release Notes 2.3: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-2.3-Release-Notes
- Whats new in Spring Boot 2.3: https://medium.com/@TimvanBaarsen/whats-new-in-spring-boot-2-3-22d01d036f11
- K8s: Pod Lifecycle: https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/
- K8s: Configure Liveness, Readiness and Startup Probes: https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
- Liveness and Readiness Probes with Spring Boot: https://spring.io/blog/2020/03/25/liveness-and-readiness-probes-with-spring-boot
- Spring Boot Production ready Kubernetes Probes: https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-kubernetes-probes
- Kritik an Liveness Probes: https://srcco.de/posts/kubernetes-liveness-probes-are-dangerous.html
- Spring Boot Docker: https://spring.io/guides/gs/spring-boot-docker/
- Layered Spring Boot Docker Images: https://blog.jdriven.com/2019/08/layered-spring-boot-docker-images/

### Exploring Tekton
- Tekton Project Page: https://tekton.dev/
- Tekton Hub: https://hub.tekton.dev/
- Tekton GitHub: https://github.com/tektoncd
- Speed up Maven builds in Tekton Pipelines: https://developers.redhat.com/blog/2020/02/26/speed-up-maven-builds-in-tekton-pipelines/
- Deploying an internal container registry with Minikube add-ons: https://developers.redhat.com/blog/2019/07/11/deploying-an-internal-container-registry-with-minikube-add-ons/
- Build and deploy a Docker image on Kubernetes using Tekton Pipelines: https://developer.ibm.com/devpractices/devops/tutorials/build-and-deploy-a-docker-image-on-kubernetes-using-tekton-pipelines/
- Tekton - A Way Through the Labyrinth: https://medium.com/cloud-engagement-hub/tekton-a-way-through-the-labyrinth-episode-1-1688260c61e7
- OpenShift Pipelines: https://www.openshift.com/learn/topics/pipelines
- JenkinsX: https://jenkins-x.io/
- argo: https://argoproj.github.io/argo-cd/
- Flux: https://fluxcd.io/

### Schulungen von Legital Learning

- Spring & Hibernate: https://legital.org/de/spring-hibernate/
- Build & Run: https://legital.org/de/build-and-run/
