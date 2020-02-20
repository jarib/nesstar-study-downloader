# Nesstar Downloader

Download study data from Nesstar

### Setup

	mvn install
	mkdir -p data

### Fetch all studies from a server

	mvn exec:java \
		-Dexec.mainClass="no.jaribakken.nesstar.NesstarStudyDownloader" \
		-Dexec.cleanupDaemonThreads=false \
		-Dnesstar.server="http://mma.nsd.uib.no" \
		-Dnesstar.username="john.doe" \
		-Dnesstar.password="s3cret" \
		-Dnesstar.output="data/"

### Fetch a specific study

	mvn exec:java \
		-Dexec.mainClass="no.jaribakken.nesstar.NesstarStudyDownloader" \
		-Dexec.cleanupDaemonThreads=false \
		-Dnesstar.server="http://mma.nsd.uib.no" \
		-Dnesstar.username="john.doe" \
		-Dnesstar.password="s3cret" \
		-Dnesstar.output="data/"
		-Dnesstar.study="MMA0000"

### Debug logging

Add

		-Dnesstar.debug=true




