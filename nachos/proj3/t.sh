#!/bin/bash
make
echo '''----->first line in test
----->second line in test
----->third line in test
----->forth line in test
----->fifth line in test''' > ../test/test

echo '''
write1


write10


cat test


cp test test2


cat test2


cat test


mv test2 test22


cat test2


cat test22


rm test22


cat test22


rm test


matmult


sort


exit''' | ../bin/nachos
