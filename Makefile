JAVAC=javac
JFLAGS=-g# -Xlint:all

default: .echo

outdirs:
	mkdir -p out

.echo: outdirs $(wildcard src/annamod/*.java)
	[ -n "$(ANNA_DIR)" ] || (echo please set env var ANNA_DIR to anna dir && exit 1)
	[ -f "$(ANNA_DIR)/out/anna.jar" ] || (cd "$(ANNA_DIR)" && make .anna)
	@[ ! -f "out/mod_plchat.jar" ] || rm out/mod_plchat.jar
	$(JAVAC) $(JFLAGS) -d out -cp "$(ANNA_DIR)/anna/out" $(filter-out $<,$^)
	jar cfM out/mod_plchat.jar -C out .
	cp out/mod_plchat.jar "$(ANNA_DIR)/out"

clean:
	rm -r out/*

