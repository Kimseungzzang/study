import http from 'k6/http';
import { check, sleep  } from 'k6';

export const options = {
    vus: 20,
    duration: '10s',
};

export default function () {

    const response = http.post('http://localhost:8080/auth/async');

    check(response, {
        'status is 200': (r) => r.status === 200,
    });
    sleep(1);
}