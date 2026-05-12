import http from 'k6/http';
import { check, sleep } from 'k6';

// 테스트 시나리오: 동시 사용자 수를 점진적으로 늘려가며 로그인 부하 테스트
export let options = {
    vus: 30,
    duration: '40s',
    thresholds: {
        http_req_duration: ['p(95)<3000'],
        http_req_failed:   ['rate<0.1'],
    },
};

const BASE_URL = 'http://localhost:8080';

// user1 ~ user20 중 랜덤 선택
function randomUser() {
    const n = Math.floor(Math.random() * 20) + 1;
    return { username: `user${n}`, password: 'password123' };
}

export default function () {
    const user = randomUser();

    const res = http.post(
        `${BASE_URL}/auth/login`,
        JSON.stringify(user),
        { headers: { 'Content-Type': 'application/json' } }
    );

    check(res, {
        '로그인 성공': (r) => r.status === 200,
        '응답시간 1초 이내': (r) => r.timings.duration < 1000,
    });
}
