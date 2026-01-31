# AI-Enabled Coding Interview - Rate Limiter Challenge

Use this session to familiarize yourself with AI-assisted problem solving for real-world distributed systems challenges.

## The Problem

You're building a **Rate Limiter Service** for an API gateway that needs to enforce request rate limits for different clients and API endpoints.

### Current Implementation

We've provided a basic rate limiter implementation that has:
- Multiple rate limiting algorithms (Token Bucket, Sliding Window)
- Client-based rate limiting
- In-memory storage
- Basic REST API

### Issues with Current Code

**There are intentional bugs in the codebase** - you'll need to find and fix them!

Some tests are commented out. Your job is to:
1. Uncomment the failing tests
2. Find and fix the bugs
3. Implement missing features
4. Optimize the solution

## Your Tasks

### Phase 1: Explore & Fix (15-20 min)
1. **Understand the code** - Read through the provided classes
2. **Run the application** - See current behavior
3. **Run unit tests** - Uncomment the commented tests
4. **Fix bugs** - There are at least 3 intentional bugs:
   - Token bucket refill logic has a subtle timing bug
   - Sliding window implementation doesn't clean up old entries
   - Concurrent access causes race conditions

### Phase 2: Implement Features (20-25 min)
Choose 2-3 features to implement based on priority:

**High Priority:**
- [ ] Add **distributed rate limiting** using a shared cache/Redis simulation
- [ ] Implement **tiered rate limits** (different limits for different subscription tiers)
- [ ] Add **endpoint-specific rate limiting** (different limits per API path)

**Medium Priority:**
- [ ] Add **rate limit headers** in responses (X-RateLimit-Remaining, X-RateLimit-Reset)
- [ ] Implement **burst allowance** (allow temporary bursts above limit)
- [ ] Add **graceful degradation** when rate limiter fails

**Nice to Have:**
- [ ] Add **metrics/monitoring** for rate limit hits
- [ ] Implement **custom time windows** (per minute, hour, day)
- [ ] Add **whitelist/blacklist** for certain clients

### Phase 3: Testing & Optimization (10-15 min)
- Write tests for your new features
- Test concurrent access scenarios
- Verify performance with many clients
- Document your approach

## Running the Code

```bash
# Compile and run
mvn clean compile
mvn test

# Run the application
mvn exec:java -Dexec.mainClass="com.meta.ratelimiter.RateLimiterApp"
```

## API Endpoints

The application exposes:

```
POST /api/check-limit
{
  "clientId": "client-123",
  "endpoint": "/api/users"
}

Response: 200 OK (allowed) or 429 Too Many Requests (rate limited)
```

## Code Structure

```
src/main/java/com/meta/ratelimiter/
├── RateLimiterApp.java          # Main application & REST API
├── RateLimiter.java              # Core rate limiter interface
├── TokenBucketRateLimiter.java  # Token bucket implementation (HAS BUGS)
├── SlidingWindowRateLimiter.java # Sliding window implementation (HAS BUGS)
├── RateLimitConfig.java          # Configuration
├── ClientRateLimitStore.java    # In-memory storage (HAS CONCURRENCY BUGS)
└── RateLimitStrategy.java        # Strategy enum

src/test/java/com/meta/ratelimiter/
├── TokenBucketTest.java          # Unit tests (some commented out)
├── SlidingWindowTest.java        # Unit tests (some commented out)
└── ConcurrencyTest.java          # Concurrency tests (commented out)
```

## Use the AI Assistant

You have access to Claude Sonnet 4.5. Use it effectively:

**Good prompts:**
- "Explain how the token bucket algorithm should work and check if the implementation is correct"
- "I'm getting a race condition in concurrent tests. Here's my code: [paste]. What's wrong?"
- "Help me design a distributed rate limiter. What are the key considerations?"

**Not-so-good prompts:**
- "Write all the code for distributed rate limiting"
- "Fix everything"

## Evaluation Criteria

### What we're looking for:
**Systematic debugging** - Finding bugs methodically, not randomly
**Algorithm understanding** - Explaining token bucket vs sliding window trade-offs
**Concurrent programming** - Handling thread safety correctly
**Design thinking** - Discussing distributed systems challenges
**AI collaboration** - Using AI effectively for specific help

### Red flags:
Not testing your changes
Copying code without understanding
Over-engineering simple solutions
Ignoring concurrency issues

## Expected Outcomes

**Minimum:**
- Find and fix at least 2 bugs
- All existing tests pass
- Implement 1 new feature that works correctly

**Strong:**
- Fix all 3+ bugs
- Implement 2-3 features
- Add comprehensive tests
- Handle concurrency correctly
- Discuss production considerations

**Exceptional:**
- All of above
- Elegant, production-ready code
- Performance optimizations
- Clear design trade-off discussions
- Alternative approaches considered

## Time Management

- **0-5 min:** Read code, understand architecture
- **5-20 min:** Find and fix bugs, pass tests
- **20-40 min:** Implement new features
- **40-50 min:** Test and validate
- **50-60 min:** Prepare discussion points

## Hints

<details>
<summary>Click for hints about the bugs (try to find them first!)</summary>

**Bug #1:** Token bucket refill calculation doesn't account for time precision correctly
**Bug #2:** Sliding window doesn't remove expired timestamps, causing memory growth
**Bug #3:** ClientRateLimitStore has race conditions in concurrent access

</details>

## Discussion Topics (Prepare to Answer)

1. **Algorithm choice:** When would you use token bucket vs sliding window vs fixed window?
2. **Distributed systems:** How would you implement this across multiple servers?
3. **Trade-offs:** Memory vs accuracy vs latency in rate limiting?
4. **Production concerns:** What metrics would you track? How to handle failures?
5. **Scalability:** How does your solution scale to millions of clients?

---

Good luck! Remember: this tests your problem-solving process and AI collaboration, not memorization.
