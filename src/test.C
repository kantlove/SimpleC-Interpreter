int add(int a, int b) {
	return a + b;
}

int double(int x) {
	return x * 2;
}

int gcd(int a, int b) {
    while(a != b) {
        if(a > b) {
            a = a - b;
		}
        else {
            b = b - a;
		}
    }
    return a;
}

void main() {
	int n;
    int m;
	int i;
	int sum;

	printf("n = ");
	scanf(n);
    printf("m = ");
	scanf(m);
	sum = 0;
	for (i = n; i <= m; i = i + 1) {
		int j;
		if (i % 2 == 0) {
			j = -i;
		} else {
			j = i;
		}
		printf(j, "\n");

		sum = add(sum, double(j));
	}
	printf("sum = ", sum, "\n");
    printf("gcd (n, m) = ", gcd(n, m), "\n");
}