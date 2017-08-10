#include "stdio.h"
#include "stdlib.h"

int main(){
	/*
	char str1[1000];
	str1[0] = 'a';
	str1[1] = 'b';
	str1[2] = 'c';
	str1[3] = '\0';
	*/
	char *str1 = "abcabcabc";
	char *str2 = "def";
	printf("%s has length %d\n", str1, strlen(str1));
	printf("%s has length %d\n", str2, strlen(str2));
	printf("end.\n");
	///*	
	//char *str5 = strcat(str1, str2);
	//printf("str1 + str2 = %s\ntotal length is %d\n", str5, strlen(str5));
	
	char *str3 = "abz";
	char *str4 = "sfds";
	char *str6 = strcpy(str4, str2);
	//printf("%s %s:\n %d, %d\n", strcmp(str3, str4), strncmp(str3, str4, 3), strncmp(str3, str4, 100));
	//*/
	return 0;
}
