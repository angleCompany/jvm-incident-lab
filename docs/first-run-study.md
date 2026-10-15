# First-run study: five developers, five minutes each

Status: **protocol prepared; no external participants recruited or results collected yet**.

The first question is whether a developer can run and understand incident 001. Star count is not the success criterion for this study.

## Participant task

Send the repository link and this instruction, without a personal walkthrough:

> Try JVM Incident Lab using only its README. Run the failure and the fix, find one piece of evidence for the failure, then describe what changed. Stop after five minutes if you get stuck. Please submit a first-run feedback issue, including failures.

Korean version:

> 저장소 README만 보고 실패 사례와 수정 사례를 실행해주세요. 실패를 뒷받침하는 증거 하나를 찾아 무엇을 바꿨는지 설명해주세요. 막히면 5분 뒤 중단해도 됩니다. 실패한 경우도 첫 실행 피드백으로 남겨주세요.

## Record consistently

Use the [first-run feedback form](https://github.com/angleCompany/jvm-incident-lab/issues/new?template=first-run-feedback.yml).

For each consenting participant, record an anonymous ID, platform/JDK, outcome, approximate time, whether additional help was needed, their explanation, interest in another incident, and intended use. Do not infer successful execution from a star, page view, or an automated test.

If the facilitator helps, preserve the original outcome as 'help needed'; a later successful run does not count as unaided success. Avoid teaching the mechanism before asking the participant to explain it.

## Proposed decision rules

These are working thresholds, not statistically validated market estimates:

- At least 3 of 5 complete the comparison without additional help.
- At least 3 of 5 can link the waiting parents, shared executor, and queued children.
- At least 2 of 5 want to try another incident.
- Record concrete intended uses separately from polite positive reactions.

If people cannot start, fix installation/docs before adding scenarios. If they can start but cannot explain the failure, improve evidence presentation. If they understand but have no reason to return, test a different scenario or audience.

Five observations guide the next iteration; they do not establish broad demand or predict GitHub stars.

## Invitation draft

Use only when you choose a recipient and explicitly authorize sending it:

> I built a small open-source Java incident lab. It reproduces thread pool starvation, captures real JVM evidence, and verifies a specific fix. Could you spend five minutes trying it using only the README and tell me where you get stuck? No positive review or GitHub star is expected. https://github.com/angleCompany/jvm-incident-lab

한국어:

> Java 스레드 풀 고갈을 직접 재현하고 실제 덤프·JFR로 확인하는 작은 오픈소스 실습 도구를 만들었습니다. README만 보고 5분 정도 실행해보신 뒤 막히는 부분을 알려주실 수 있을까요? 좋은 평가나 스타를 부탁드리는 것은 아닙니다. https://github.com/angleCompany/jvm-incident-lab
