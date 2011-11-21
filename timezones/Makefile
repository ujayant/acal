
all:	vtimezones/US/Eastern.ics vtimezones/US/Pacific.ics vtimezones/US/Mountain.ics vtimezones/US/Central.ics ../res/values/timezones.xml

vtimezones/US/Eastern.ics:	vtimezones/US vtimezones/America/New_York.ics
	cd vtimezones/US && ln -sf ../America/New_York.ics Eastern.ics

vtimezones/US/Pacific.ics:	vtimezones/US vtimezones/America/Los_Angeles.ics
	cd vtimezones/US && ln -sf ../America/Los_Angeles.ics Pacific.ics

vtimezones/US/Mountain.ics:	vtimezones/US vtimezones/America/Denver.ics
	cd vtimezones/US && ln -sf ../America/Denver.ics Mountain.ics

vtimezones/US/Central.ics:	vtimezones/US vtimezones/America/Chicago.ics
	cd vtimezones/US && ln -sf ../America/Chicago.ics Central.ics

vtimezones/US: vtimezones/America
	rm -rf vtimezones/US
	mkdir -p vtimezones/US
	touch vtimezones/US

../res/values/timezones.xml: build-acal-zones.pl vtimezones/Pacific/Auckland.ics
	./build-acal-zones.pl --strings --output ../res/values/timezonenames.xml
	./build-acal-zones.pl --output ../res/values/timezones.xml

