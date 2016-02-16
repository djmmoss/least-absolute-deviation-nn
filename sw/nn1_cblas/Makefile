CC = icc
MKLROOT = /opt/intel/compilers_and_libraries/mac/mkl
CFLAGS = -g -Wall
INCLUDES = -I${MKLROOT}/include
LIBS = -L${MKLROOT}/lib -mkl

SOURCES=$(wildcard *.c)
OBJECTS=$(SOURCES:.c=.o)
EXEC = nn1_lad


EXEC: $(OBJECTS)
	$(CC) $(CFLAGS) $(INCLUDES) $(LIBS) $(OBJECTS) -o $(EXEC)

%.o: %.c
	$(CC) -c $(CFLAGS) $(INCLUDES) $< -o $@

clean:
	$(RM) $(EXEC) $(OBJECTS)
